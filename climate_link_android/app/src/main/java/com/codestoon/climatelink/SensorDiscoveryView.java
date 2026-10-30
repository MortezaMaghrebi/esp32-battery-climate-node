package com.codestoon.climatelink;
import android.content.Context;import android.graphics.*;import android.util.AttributeSet;import android.view.View;
/** Decorative sensor enclosure concept, not a representation of the current PCB. */
public final class SensorDiscoveryView extends View {
 private final Paint p=new Paint(3);
 public SensorDiscoveryView(Context c,AttributeSet a){super(c,a);setLayerType(LAYER_TYPE_SOFTWARE,null);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density,x=getWidth()/2f,y=getHeight()/2f;
 p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d);p.setColor(0x5571C7F0);for(int i=1;i<4;i++)c.drawCircle(x,y,(35+i*22)*d,p);
 p.setStyle(Paint.Style.FILL);p.setColor(0xffF9FCFF);p.setShadowLayer(10*d,0,8*d,0x30417696);RectF box=new RectF(x-43*d,y-59*d,x+43*d,y+59*d);c.drawRoundRect(box,24*d,24*d,p);p.clearShadowLayer();
 p.setShader(new LinearGradient(x-43*d,y,x+43*d,y,0xffFFFFFF,0xffDCEAF2,Shader.TileMode.CLAMP));c.drawRoundRect(new RectF(x-38*d,y-54*d,x+38*d,y+54*d),21*d,21*d,p);p.setShader(null);
 p.setColor(0xff607D93);for(int row=-2;row<=2;row++)for(int col=-2;col<=2;col++)if(row*row+col*col<6)c.drawCircle(x+col*4*d,y+row*4*d,0.85f*d,p);
 p.setColor(0xff199FE3);c.drawCircle(x,y+31*d,2*d,p);
 }
}