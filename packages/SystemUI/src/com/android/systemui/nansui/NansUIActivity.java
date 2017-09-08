/*
 * Copyright (C) 2017 RUBIS Laboratory at Seoul National University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.util.Log;
import android.util.Slog;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.List;

public class NansUIActivity extends Activity {
    /** LAYOUT **/
    private RelativeLayout mainLayout;

    /** IMPORTED CLASS **/
    private ArrayList<DisplayIcon> displayIcons;
    private AppIcon appIcon;

    /** SET MANAGERS **/
    private IActivityManager iam;
    private ActivityManager am;
    private DisplayManager dm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.nans_app);
        mainLayout = (RelativeLayout)findViewById(R.id.mainLayout);

        LinearLayout viewTable = (LinearLayout)findViewById(R.id.viewTable);
        viewTable.setAlpha(0.5f);
        viewTable.setBackgroundColor(Color.WHITE);
        iam = (IActivityManager)ActivityManagerNative.getDefault();
        am = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
        dm = (DisplayManager)getSystemService(DISPLAY_SERVICE);

        // set display icon.
        displayIcons = new ArrayList<>();
        displayIcons.add(new DisplayIcon(this, DisplayIcon.PHONE));
        displayIcons.add(new DisplayIcon(this, DisplayIcon.PC));
        displayIcons.add(new DisplayIcon(this, DisplayIcon.TV));

        for (DisplayIcon icon : displayIcons) {
            mainLayout.addView(icon);

            switch (icon.getType()) {
                case DisplayIcon.PHONE:
                    icon.setPosition(700f, 100f);
                    break;
                case DisplayIcon.PC:
                    icon.setPosition(200f, 550f);
                    break;
                case DisplayIcon.TV:
                    icon.setPosition(1200f, 550f);
                    break;
                default:
                    break;
            }

            icon.setVisibility(View.INVISIBLE);
        }

        String packageName = "com.android.messaging";

        try {
            Drawable appImage = getPackageManager().getApplicationIcon(packageName);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(appImage);
            iv.setLayoutParams(layoutParams);
            appIcon = new AppIcon(this, packageName, appImage, 0);
            mainLayout.addView(appIcon);
            appIcon.setPosition(0.45f, 0.45f);
            appIcon.setOnTouchListener(new AppTouchListener());
        } catch (Exception e) {

        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Display[] displays = dm.getDisplays();

        int numOfDisplays = displays.length;
        for (int it = 0; it < numOfDisplays; ++it)
            displayIcons.get(it).setVisibility(View.VISIBLE);
        try {
            List<ActivityManager.RecentTaskInfo> recentTasks = iam.getRecentTasks(Integer.MAX_VALUE, ActivityManager.RECENT_IGNORE_UNAVAILABLE, 0).getList();
            String packageName = "";
            for (int j = 1; j < recentTasks.size(); ++j) {
                ActivityManager.RecentTaskInfo currTask = recentTasks.get(j);
                int currDisplayId = iam.getDisplayIdByTaskId(currTask.id);
                if (currDisplayId == 0) {
                    packageName = currTask.baseActivity.getPackageName();
                    break;
                }
            }
            Drawable appImage = getPackageManager().getApplicationIcon(packageName);
            appIcon.replace(packageName, appImage);
            appIcon.setPosition(0.45f, 0.45f);
        } catch (Exception e) {

        }

        try {
            List<ActivityManager.RunningTaskInfo> runningTasks = iam.getTasks(Integer.MAX_VALUE, 0);
            for (int i = 1; i < displays.length; ++i) {
                int displayId = displays[i].getDisplayId();
                int taskId = iam.getTaskIdByDisplayId(displayId);
                if (taskId != -1) {
                    for (int j = 0; j < runningTasks.size(); ++j) {
                        ActivityManager.RunningTaskInfo task = runningTasks.get(j);
                        if (taskId == task.id) {
                            String packageName = task.baseActivity.getPackageName();
                            Drawable appImage = getPackageManager().getApplicationIcon(packageName);
                            AppIcon externalAppIcon = new AppIcon(this, packageName, appImage, displayId);
                            mainLayout.addView(externalAppIcon);
                            externalAppIcon.setPosition((float)(-0.4 + 0.55 * i), 0.65f);
                            externalAppIcon.setOnTouchListener(new AppTouchListener());
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
        
        }

    }

    private class AppTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(final View view, MotionEvent motionEvent) {
            switch(motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    break;
                case MotionEvent.ACTION_UP:
                    final int viewPosition = whereIsThe(view);
                    if (viewPosition == displayIcons.size()) {
                        view.animate().translationX(((AppIcon) view).rootX).withLayer();
                        view.animate().translationY(((AppIcon) view).rootY).withLayer();
                    } else {
                        final Display[] displays = dm.getDisplays();
                        DisplayIcon displayIcon = displayIcons.get(viewPosition);
                        int prevDisplayId = ((AppIcon) view).displayId;
                        int nextDisplayId = displays[viewPosition].getDisplayId();
                        if (prevDisplayId != 0 && nextDisplayId != 0) {
                            try {
                                int prevTaskId = iam.getTaskIdByDisplayId(prevDisplayId);
                                int nextTaskId = iam.getTaskIdByDisplayId(nextDisplayId);
                                if (prevTaskId != -1) {
//                                    am.moveTaskToFront(prevTaskId, ActivityManager.MOVE_TASK_NO_USER_ACTION);
                                    am.setExternalDisplay(((AppIcon) view).getPackageName(), displays[viewPosition]);
                                    ActivityCompat.finishAffinity(NansUIActivity.this);
                                    return true;
                                }
                            } catch (Exception e) {

                            }
                        }
                        am.setExternalDisplay(((AppIcon) view).getPackageName(), displays[viewPosition]);
//                        Handler setExternalDisplayHandler = new Handler(Looper.getMainLooper());
//                        Runnable setExternalDisplayRunnable = new Runnable() {
//                            @Override
//                            public void run() {
//                                am.setExternalDisplay(((AppIcon) view).getPackageName(), displays[viewPosition]);
//                            }
//                        };
//                        setExternalDisplayHandler.postDelayed(setExternalDisplayRunnable, 50);
                        Handler handler = new Handler(Looper.getMainLooper());
                        Runnable runnable = new Runnable() {
                            @Override
                            public void run() {
                                am.setExternalDisplay(((AppIcon) view).getPackageName(), displays[viewPosition]);
                                ActivityCompat.finishAffinity(NansUIActivity.this);
                            }
                        };
                        handler.postDelayed(runnable, 50);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    ((AppIcon)view).coordX = (int)(motionEvent.getRawX() - view.getWidth()/2);
                    ((AppIcon)view).coordY = (int)(motionEvent.getRawY() - view.getHeight()/2);
                    view.setX(((AppIcon)view).coordX);
                    view.setY(((AppIcon)view).coordY);
                    break;
            }

            return true;
        }
    }

    private int whereIsThe(View view) {
        for (int i = 0; i < displayIcons.size(); ++i) {
            DisplayIcon displayIcon = displayIcons.get(i);
            if (displayIcon.getX() < view.getX() && view.getX() < (displayIcon.getX() + displayIcon.getWidth() - view.getWidth()) &&
                    displayIcon.getY() < view.getY() && view.getY() < (displayIcon.getY() + displayIcon.getHeight() - view.getHeight()) &&
                    (displayIcon.getVisibility() == View.VISIBLE)) {
                return i;
            }
        }
        return displayIcons.size();
    }

}

