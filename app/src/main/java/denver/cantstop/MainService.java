package denver.cantstop;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainService extends Service {

    private WeakReference<MainService> selfRef;
    private WorkerThread workerThread;

    @Override
    public void onCreate() {
        log("MainService.onCreate()");
        super.onCreate();
        selfRef = new WeakReference<>(this);
    }

    @Override
    public void onDestroy() {
        log("MainService.onDestroy()");
        selfRef.clear();
        if (workerThread != null) {
            workerThread.cancel();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        log("MainService.onBind()");
        throw new UnsupportedOperationException("bind not supported");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("MainService.onStartCommand() intent=" + intent);
        if (workerThread == null) {
            log("MainService.onStartCommand() sarting WorkerThread");
            workerThread = new WorkerThread(selfRef);
            workerThread.start();
        }
        return START_NOT_STICKY;
    }

    void onWorkerThreadComplete(WorkerThread workerThread) {
        log("MainService.onWorkerThreadComplete()");
        if (workerThread == null || workerThread != this.workerThread) {
            throw new IllegalArgumentException("incorrect workerThread: " + workerThread);
        }
        this.workerThread = null;
    }

    static final class DocumentSnapshotListener implements EventListener<DocumentSnapshot> {

        private final Object lock = new Object();
        private int count = 0;

        @Override
        public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
            log("DocumentSnapshotListener.onEvent()");
            synchronized (lock) {
                count++;
                lock.notifyAll();
            }
        }

        public void waitForEvent() {
            synchronized (lock) {
                while (count == 0) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        log("WARNING DocumentSnapshotListener.waitForEvent() " + e);
                        break;
                    }
                }
            }
        }

    }

    private static final class WorkerThread extends Thread {

        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final WeakReference<MainService> serviceRef;
        private final Executor executor = Executors.newSingleThreadExecutor();
        private volatile boolean cancelled;

        public WorkerThread(WeakReference<MainService> serviceRef) {
            this.serviceRef = serviceRef;
        }

        @Override
        public void run() {
            for (int i=0; i<10; i++) {
                log("Test " + i + " starting");
                runTest();
            }

            log("All tests complete");

            mainHandler.post(() -> {
                MainService mainService = serviceRef.get();
                if (mainService != null) {
                    mainService.onWorkerThreadComplete(WorkerThread.this);
                }
            });
        }

        private void runTest() {
            log("firestore = FirebaseFirestore.getInstance()");
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            log("doc = firestore.document(\"abc/123\")");
            DocumentReference doc = firestore.document("abc/123");

            log("listenerRegistration = doc.addSnapshotListener()");
            DocumentSnapshotListener listener = new DocumentSnapshotListener();
            ListenerRegistration listenerRegistration = doc.addSnapshotListener(executor, listener);
            log("waitForListenerCallback()");
            listener.waitForEvent();

            log("firestore.terminate()");
            Task<Void> terminateTask1 = firestore.terminate();
            waitForTaskToComplete(terminateTask1);

            log("listenerRegistration.remove()");
            listenerRegistration.remove();

            log("firestore.terminate() again");
            Task<Void> terminateTask2 = firestore.terminate();
            waitForTaskToComplete(terminateTask2);
        }

        private void waitForTaskToComplete(Task<Void> task) {
            final AtomicBoolean taskComplete = new AtomicBoolean(false);

            task.addOnCompleteListener(executor, task1 -> {
                synchronized (taskComplete) {
                    taskComplete.set(true);
                    taskComplete.notifyAll();
                }
            });

            while (true) {
                synchronized (taskComplete) {
                    if (taskComplete.get()) {
                        break;
                    }
                    try {
                        taskComplete.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }

        public void cancel() {
            cancelled = true;
            interrupt();
        }

    }

    static void log(String message) {
        Log.i("zzyzx", message);
    }

}
