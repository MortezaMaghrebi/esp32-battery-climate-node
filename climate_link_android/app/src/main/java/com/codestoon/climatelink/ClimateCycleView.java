package com.codestoon.climatelink;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

/** Static process diagram, never a live state or a measurement scale. */
public final class ClimateCycleView extends View {
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ClimateCycleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float step = getWidth() / 4f, y = getHeight() / 2f, radius = 4f * density;
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(density);
        ink.setColor(ContextCompat.getColor(getContext(), R.color.cl_field_outline));
        for (int i = 0; i < 3; i++) {
            float x = step * (i + .5f);
            canvas.drawLine(x + radius, y, x + step - radius, y, ink);
        }
        ink.setColor(ContextCompat.getColor(getContext(), R.color.cl_muted));
        for (int i = 0; i < 4; i++) canvas.drawCircle(step * (i + .5f), y, radius, ink);
    }
}
