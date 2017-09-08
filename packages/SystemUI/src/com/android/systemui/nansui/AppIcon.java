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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.AppCompatImageView;
import android.view.View;

public class AppIcon extends AppCompatImageView {

    private String packageName;
    private Drawable iconImage;
    int rootX;
    int rootY;
    int coordX;
    int coordY;
    int displayId;

    public AppIcon(Context context, String packageName, Drawable iconImage, int displayId) {
        super(context);

        this.setId(View.generateViewId());
        this.packageName = packageName;
        this.iconImage = iconImage;
        this.setImageDrawable(this.iconImage);
        this.displayId = displayId;
    }

    public void replace(String packageName, Drawable iconImage) {
        this.packageName = packageName;
        this.iconImage = iconImage;
        this.setImageDrawable(this.iconImage);
    }

    public void setPosition(float x, float y) {
        this.setX(x*1920);
        this.setY(y*1080);
        rootX = (int)this.getX();
        rootY = (int)this.getY();
        coordX = (int)this.getX();
        coordY = (int)this.getY();
    }

    public String getPackageName() {
        return this.packageName;
    }

}

