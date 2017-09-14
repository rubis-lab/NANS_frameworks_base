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
 * If you want to use as commercial, contact us(rubis.chief@gmail.com).
 */

package com.android.server.accessibility;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.MathUtils;
import android.util.Slog;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

import android.app.ActivityManager;
import android.app.ActivityManager.StackInfo;
import android.app.ActivityManager.AppTask;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.hardware.display.DisplayManager;
import android.os.RemoteException;
import android.view.Display;
import android.view.WindowManager;
import android.view.WindowManagerInternal;

import java.util.List;
import java.util.ArrayList;

/**
 * This class handles NANS feature in response to swipe gestures.
 *
 * The behavior is as follows:
 *
 * 1. (Example) Triple tap toggles permanent screen magnification which is magnifying
 *    the area around the location of the triple tap. One can think of the
 *    location of the triple tap as the center of the magnified viewport.
 *    For example, a triple tap when not magnified would magnify the screen
 *    and leave it in a magnified state. A triple tapping when magnified would
 *    clear magnification and leave the screen in a not magnified state.
 *
 */
class SwipeGestureHandler implements EventStreamTransformation {
    private static final String TAG = "SwipeGestureHandler";
    private static final boolean DEBUG_SWIPE = false;

    private EventStreamTransformation mNext;

    static final int FIRST_EXTERNAL_DISPLAY     = 1;
    static final int SECOND_EXTERNAL_DISPLAY    = 2;
    static final int THIRD_EXTERNAL_DISPLAY     = 3;
    static final int FOURTH_EXTERNAL_DISPLAY    = 4;

    static final int BLANK = 0;
    static final int MIRRORED = 1;
    static final int MIRRORING = 2;
    static final int NANS = 3;

    int GLOBAL_TOUCH_POSITION_X         = 0;
    int GLOBAL_TOUCH_CURRENT_POSITION_X = 0;
    int GLOBAL_TOUCH_POSITION_Y         = 0;
    int GLOBAL_TOUCH_CURRENT_POSITION_Y = 0;

    private Context mContext;
    private IActivityManager iam;
    private ActivityManager am;
    private WindowManager wm;
    private DisplayManager dm;    

    public SwipeGestureHandler(Context context) {
        mContext = context;
        iam = (IActivityManager)ActivityManagerNative.getDefault();
        am = (ActivityManager)mContext.getSystemService(Context.ACTIVITY_SERVICE);
        wm = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        dm = (DisplayManager)mContext.getSystemService(Context.DISPLAY_SERVICE);
    }

    @Override
    public void onMotionEvent(MotionEvent m, MotionEvent rawEvent, int policyFlags) {
        if (!m.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
            if (mNext != null) {
                mNext.onMotionEvent(m, rawEvent, policyFlags);
            }
            return;
        }
        int pointerCount = m.getPointerCount();
        if (pointerCount == 3){
            int action = m.getActionMasked();
            int actionIndex = m.getActionIndex();

            int width = wm.getDefaultDisplay().getWidth();
            int height = wm.getDefaultDisplay().getHeight();

            int MIN_DIFF = (int)(0.2*height);
            int MIN_HORIZONTAL_DIFF = (int)(0.2*width);
            int TOP_BOUND = (int)(0.3*height);
            int MID_UPPER_BOUND = (int)(0.3*height);
            int MID_LOWER_BOUND = (int)(0.75*height);
            int BOTTOM_BOUND = (int)(0.75*height);
            int LEFT_BOUND = (int)(0.4*width);
            int RIGHT_BOUND = (int)(0.6*width);

            String actionString;
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    GLOBAL_TOUCH_POSITION_X = (int) m.getX(1);
                    GLOBAL_TOUCH_POSITION_Y = (int) m.getY(1);
                    if (DEBUG_SWIPE) 
                        Slog.d(TAG, "DOWN: (" 
                                + GLOBAL_TOUCH_POSITION_X + ", " 
                                + GLOBAL_TOUCH_POSITION_Y + ")");
                    break;
                case MotionEvent.ACTION_UP:
                    GLOBAL_TOUCH_CURRENT_POSITION_X = 0;
                    GLOBAL_TOUCH_CURRENT_POSITION_Y = 0;
                    if (DEBUG_SWIPE) 
                        Slog.d(TAG, "UP: (" 
                                + GLOBAL_TOUCH_CURRENT_POSITION_X + ", " 
                                + GLOBAL_TOUCH_CURRENT_POSITION_Y + ")");
                    break;
                case MotionEvent.ACTION_MOVE:
                    GLOBAL_TOUCH_CURRENT_POSITION_X = (int) m.getX(1);
                    int diff_x = GLOBAL_TOUCH_POSITION_X-GLOBAL_TOUCH_CURRENT_POSITION_X;
                    GLOBAL_TOUCH_CURRENT_POSITION_Y = (int) m.getY(1);
                    int diff_y = GLOBAL_TOUCH_POSITION_Y-GLOBAL_TOUCH_CURRENT_POSITION_Y;
                    if (DEBUG_SWIPE) Slog.d(TAG, "Move: (" + diff_x + ", " + diff_y + ")");
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    GLOBAL_TOUCH_POSITION_X = (int) m.getX(1);
                    GLOBAL_TOUCH_POSITION_Y = (int) m.getY(1);
                    if (DEBUG_SWIPE) 
                        Slog.d(TAG, "Pointer DOWN: (" 
                            + GLOBAL_TOUCH_POSITION_X + ", " 
                            + GLOBAL_TOUCH_POSITION_Y + ")");
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    GLOBAL_TOUCH_CURRENT_POSITION_X = (int) m.getX(1);
                    int dif_x = GLOBAL_TOUCH_POSITION_X-GLOBAL_TOUCH_CURRENT_POSITION_X;
                    GLOBAL_TOUCH_CURRENT_POSITION_Y = (int) m.getY(1);
                    int dif_y = GLOBAL_TOUCH_POSITION_Y-GLOBAL_TOUCH_CURRENT_POSITION_Y;
                    if (DEBUG_SWIPE) 
                        Slog.d(TAG, "Pointer UP: Diff " + dif_x + "/" + dif_y + ", pc=" + pointerCount);

                    if (GLOBAL_TOUCH_POSITION_X <= LEFT_BOUND && 
                            GLOBAL_TOUCH_CURRENT_POSITION_X >= RIGHT_BOUND && 
                            dif_x <= -MIN_HORIZONTAL_DIFF) {
                        if (DEBUG_SWIPE) Slog.d(TAG, "--Left to Right--");
                        traverseTasksOnDisplays(true);
                        break;
                    } else if (GLOBAL_TOUCH_POSITION_X >= RIGHT_BOUND && 
                            GLOBAL_TOUCH_CURRENT_POSITION_X <= LEFT_BOUND && 
                            dif_x >= MIN_HORIZONTAL_DIFF) {
                        if (DEBUG_SWIPE) Slog.d(TAG, "--Right to Left--");
                        traverseTasksOnDisplays(false);
                        break;
                    }

                    if (GLOBAL_TOUCH_POSITION_Y >= MID_UPPER_BOUND && 
                            GLOBAL_TOUCH_POSITION_Y <= MID_LOWER_BOUND) {
                        if (dif_y >= MIN_DIFF && GLOBAL_TOUCH_CURRENT_POSITION_Y <= TOP_BOUND) {
                            if (DEBUG_SWIPE) Slog.d(TAG, "--Mid to Top--");
                            sendTaskToExternalDisplay(FIRST_EXTERNAL_DISPLAY);
                        } else if (dif_y <= -MIN_DIFF && GLOBAL_TOUCH_CURRENT_POSITION_Y >= BOTTOM_BOUND){
                            if (DEBUG_SWIPE) Slog.d(TAG, "--Mid to Bottom--");
                            sendTaskToExternalDisplay(SECOND_EXTERNAL_DISPLAY);
                        }
                    } else if( GLOBAL_TOUCH_CURRENT_POSITION_Y >= MID_UPPER_BOUND &&
                            GLOBAL_TOUCH_CURRENT_POSITION_Y <= MID_LOWER_BOUND ){
                        if (GLOBAL_TOUCH_POSITION_Y <= TOP_BOUND && dif_y <= -MIN_DIFF) {
                            if (DEBUG_SWIPE) Slog.d(TAG, "Top to Mid");
                            bringTaskToDefaultDisplay(FIRST_EXTERNAL_DISPLAY);
                        } else if (GLOBAL_TOUCH_POSITION_Y >= BOTTOM_BOUND && dif_y >= MIN_DIFF) {
                            if (DEBUG_SWIPE) Slog.d(TAG, "Bottom to Mid");
                            bringTaskToDefaultDisplay(SECOND_EXTERNAL_DISPLAY);
                        }
                    }
                    break;
                default:
                    actionString = "";
            }
            pointerCount = 0;
        } else {
            GLOBAL_TOUCH_POSITION_X = 0;
            GLOBAL_TOUCH_CURRENT_POSITION_X = 0;
        }
        mNext.onMotionEvent(m, rawEvent, policyFlags);
    }

    private void sendTaskToExternalDisplay(int index) {
        try {
            final List<RunningTaskInfo> tasks = iam.getTasks(2, 0);
            Display[] displays = dm.getDisplays();
            if (displays.length > index && displays[index] != null) {
                final RunningTaskInfo task = tasks.get(0);
                if (!isHomeActivity(task)) {
                    iam.setExternalDisplay(task.id, 
                            displays[index].getLayerStack(), 
                            ActivityManager.SET_EXTERNAL_DISPLAY_AND_GO_HOME);
                }
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to send the task to external display: " + e.toString());
        }
    }

    private void bringTaskToDefaultDisplay(int index) {
        try {
            Display[] displays = dm.getDisplays();
            if (displays.length > index && displays[index] != null) {
                int displayId = displays[index].getLayerStack();
                int taskId = iam.getTaskIdByDisplayId(displayId);
                int displayMode = iam.getDisplayModeByDisplayId(displayId);
                if (displayMode == MIRRORED) {
                    iam.setExternalDisplay(taskId, displays[0].getLayerStack(),
                                ActivityManager.SET_EXTERNAL_DISPLAY_AND_STAY);
                } else if (displayMode == NANS) {
                    am.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_NO_USER_ACTION);
                }
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to bring the task to default display: " + e.toString());
        }
    }

    private void traverseTasksOnDisplays(boolean isForward) {
        try {
            int displayId = iam.getDisplayIdOfFocusedStack();
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to get the displayId of focused stack: " + e.toString());
        }

        Display[] displays = dm.getDisplays();
        if (displays.length > 1) {
            if (DEBUG_SWIPE) Slog.d(TAG, "[ getRunningTasks ]");
            try {
                List<RunningTaskInfo> tasks = iam.getTasks(20, 0);
                if (DEBUG_SWIPE) {
                    for (int i=0; i<tasks.size(); i++)
                        Slog.d(TAG, "  [" + i + "] task = " + tasks.get(i));
                }

                int taskId = getTargetTaskIdFromRecentTasks(isForward);
                if (DEBUG_SWIPE) Slog.d(TAG, "  Chosen next taskId = " + taskId);
                if (taskId != -1)
                    am.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_NO_USER_ACTION);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to traverse to the next task: " + e.toString());
            }
        }
    }

    private int getTargetTaskIdFromRecentTasks(boolean isForward) throws RemoteException {
        try {
            ArrayList<RunningTaskInfo> targetTasks = new ArrayList<RunningTaskInfo>();
            List<RunningTaskInfo> tasks = iam.getTasks(20, 0);
            List<StackInfo> stacks = iam.getAllStackInfos();

            // collect tasks for each display
            Display[] displays = dm.getDisplays();
            for (int i=0; i<displays.length; i++) {
                int displayId = displays[i].getLayerStack();
                for (int j=0; j<tasks.size(); j++) {
                    if (displayId == iam.getDisplayIdByTaskId(tasks.get(j).id)) {
                        targetTasks.add(tasks.get(j));
                        break;
                    }
                }
            }

            if (targetTasks.size() <= 1) {
                return -1;
            }

            // Get focused stack's displayId
            int displayId = iam.getDisplayIdOfFocusedStack();

            // Find current task's index
            int index = -1;
            for (int i=0; i<targetTasks.size(); i++) {
                int dId = iam.getDisplayIdByTaskId(targetTasks.get(i).id);
                if (displayId == iam.getDisplayIdByTaskId(targetTasks.get(i).id)) {
                    index = i;
                    break;
                }
            }

            // Re-ordering by focused displayId
            int count = 0;
            while (count < index) {
                RunningTaskInfo temp = targetTasks.get(0);
                targetTasks.remove(0);
                targetTasks.add(targetTasks.size(), temp);
                count++;
            }

            // Find target taskId
            return isForward ? targetTasks.get(1).id : targetTasks.get(targetTasks.size()-1).id;
        } catch (RemoteException e) {

        }
        return -1;
    }

    private boolean isHomeActivity(RunningTaskInfo task) {
        try {
            return iam.isInHomeStack(task.id);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to check whether this task is home activity: " + e.toString());
        }
        return false;
    }

    @Override
    public void onKeyEvent(KeyEvent event, int policyFlags) {
        if (mNext != null) {
            mNext.onKeyEvent(event, policyFlags);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mNext != null) {
            mNext.onAccessibilityEvent(event);
        }
    }

    @Override
    public void setNext(EventStreamTransformation next) {
        mNext = next;
    }

    @Override
    public void clearEvents(int inputSource) {
        if (mNext != null) {
            mNext.clearEvents(inputSource);
        }
    }

    @Override
    public void onDestroy() {}
}
