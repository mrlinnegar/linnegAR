package com.linnegar.james.linnegar;

import android.os.Bundle;
import android.view.View;
import android.content.Intent;
import android.app.Activity;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onSendMessage(View view) {
        Intent intent = new Intent(this, ArActivity.class);
        startActivity(intent);
    }

}
