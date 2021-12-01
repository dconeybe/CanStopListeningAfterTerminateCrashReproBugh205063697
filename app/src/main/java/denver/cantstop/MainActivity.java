package denver.cantstop;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        log("MainActivity.onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ComponentName serviceName = startService(new Intent(this, MainService.class));
        log("serviceName=" + serviceName);
    }

    static void log(String message) {
        Log.i("zzyzx", message);
    }

}