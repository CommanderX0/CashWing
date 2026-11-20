package com.example.quickpay;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

public class Anim {

    public static void fadeIn(Activity a, View v) {
        Animation anim = AnimationUtils.loadAnimation(a, R.anim.fade_in);
        v.startAnimation(anim);
    }

    public static void zoomIn(Activity a, View v) {
        Animation anim = AnimationUtils.loadAnimation(a, R.anim.zoom_in);
        v.startAnimation(anim);
    }

    public static void shake(Activity a, View v) {
        Animation anim = AnimationUtils.loadAnimation(a, R.anim.shake);
        v.startAnimation(anim);
    }

    public static void pulse(Activity a, View v) {
        Animation anim = AnimationUtils.loadAnimation(a, R.anim.pulse);
        v.startAnimation(anim);
    }

    // up = true: the money flies away (sender), false: the money falls in (receiver)
    public static void flyMoney(Activity a, View[] money, boolean up) {
        for (int i = 0; i < money.length; i++) {
            Animation anim = AnimationUtils.loadAnimation(a, up ? R.anim.fly_up : R.anim.fly_down);
            anim.setStartOffset(i * 250);
            money[i].setVisibility(View.VISIBLE);
            money[i].startAnimation(anim);
        }
    }

    public static void open(Activity a, Intent intent) {
        a.startActivity(intent);
        a.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    public static void close(Activity a) {
        a.finish();
        a.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    public static void openClear(Activity a, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(intent);
        a.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    public static void openHome(Activity a, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        a.startActivity(intent);
        a.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}
