/*
 * SPDX-FileCopyrightText: 2022 Manuel Jesús de la Fuente <m@nueljl.in>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface.Utils

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout

class RoundedConstraintLayout : ConstraintLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
        : super(context, attrs, defStyleAttr)

    @TargetApi(21)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int)
        : super(context, attrs, defStyleAttr, defStyleRes)

    var path: Path = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val width  = w.toFloat()
        val height = h.toFloat()

        // Set the corner radius here. You could change this to i.e. 28.0F to match Material 3 but
        // since this is only used in the Bigscreen activity for the circle pad it's just taking
        // the runtime width as the radius to make it fully rounded
        val radius = width

        with (this.path) {
            addRoundRect(
                RectF(0.0F, 0.0F, width, height), radius, radius, Path.Direction.CW
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas?) {
        canvas?.clipPath(this.path)
        super.dispatchDraw(canvas)
    }
}