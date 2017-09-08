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
import android.support.v7.widget.AppCompatImageView;
import android.view.View;


public class DisplayIcon extends AppCompatImageView {

    public static final byte PHONE = 0;
    public static final byte PC = 1;
    public static final byte TV = 2;

    private byte displayType;

    public DisplayIcon(Context context, byte displayType) {
        super(context);

        switch (displayType) {
            case PHONE:
                this.setImageResource(R.drawable.phone128);
                break;
            case PC:
                this.setImageResource(R.drawable.pc128);
                break;
            case TV:
                this.setImageResource(R.drawable.tv128);
                break;
            default:
                break;
        }

        this.setId(View.generateViewId());

        this.displayType = displayType;
    }

    public void setPosition(float x, float y) {
        this.setX(x);
        this.setY(y);

    }

    public byte getType() {
        return this.displayType;
    }

}
