package com.codestoon.climatelink;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/** Decorative, resolution-independent sensor illustration; does not represent live telemetry. */
public final class ClimateIllustrationView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private final Path leaf=new Path();
    public ClimateIllustrationView(Context c, AttributeSet a) { super(c,a); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width) {
        paint.setColor(color);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x1,y1,x2,y2,paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.save();canvas.scale(getWidth()/140f,getHeight()/176f);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(0xff3b6457);
        canvas.drawCircle(83,90,66,paint);canvas.drawCircle(83,90,52,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xff244f42);canvas.drawCircle(83,90,39,paint);
        paint.setColor(0xffbdd6bd);rect.set(46,44,111,140);canvas.drawRoundRect(rect,18,18,paint);
        paint.setColor(0xffe8efe0);rect.set(41,40,106,135);canvas.drawRoundRect(rect,18,18,paint);
        paint.setColor(0xffcfddcc);rect.set(51,51,96,97);canvas.drawRoundRect(rect,11,11,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setColor(0xff366b54);paint.setStrokeWidth(2.2f);paint.setStrokeCap(Paint.Cap.ROUND);
        leaf.reset();leaf.moveTo(61,85);leaf.cubicTo(57,68,71,60,87,61);leaf.cubicTo(88,76,78,89,66,83);canvas.drawPath(leaf,paint);
        line(canvas,61,87,79,69,0xff366b54,2.2f);
        line(canvas,61,111,85,111,0xff7f9d89,2);line(canvas,64,117,82,117,0xff7f9d89,2);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xff67976d);canvas.drawCircle(74,126,2,paint);
        paint.setColor(0xffb4ceac);canvas.drawCircle(120,46,5,paint);canvas.drawCircle(31,122,3,paint);
        paint.setColor(0xff628d70);canvas.drawCircle(114,145,3,paint);
        line(canvas,30,60,30,68,0xff96b294,1.5f);line(canvas,26,64,34,64,0xff96b294,1.5f);
        canvas.restore();
    }
}
