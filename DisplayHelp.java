package com.project.jean.project;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

/**
 * Class that creates an activity that displays help to the user when they click on the help button.
 */
public class DisplayHelp extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_help);//Displays the relevant activity providing help to the user.
    }

    public void onClickOk(View view)
    {
        finish();//When the Ok button is clicked, the activity closes and returns to the Home Page.
    }
}
