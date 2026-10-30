package com.codestoon.climatelink;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
/** Decorative climate instrument bezel; no scale or fabricated sensor progress. */
public final class ClimateDialView extends View {
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 public ClimateDialView(Context c,AttributeSet a){super(c,a);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
 @Override protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density;
 float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.39f;
 p.setStyle(Paint.Style.FILL);p.setColor(0xCCFFFFFF);p.setShadowLayer(12*d,0,6*d,0x22477A9D);c.drawCircle(x,y,r,p);p.clearShadowLayer();
 p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);p.setColor(0xFFFFFFFF);c.drawCircle(x,y,r,p);
 p.setStrokeWidth(8*d);p.setShader(new SweepGradient(x,y,new int[]{0xff00baf5,0xff0075fa,0xff31e1ef,0xff00baf5},null));
 c.drawArc(x-r+11*d,y-r+11*d,x+r-11*d,y+r-11*d,145,125,false,p);p.setShader(null);
 p.setStrokeWidth(d);p.setColor(0xff74c9fc);
 for(int i=0;i<64;i++){double a=(-90+i*270.0/64)*Math.PI/180;float inner=r-15*d,outer=r-9*d;c.drawLine(x+(float)Math.cos(a)*inner,y+(float)Math.sin(a)*inner,x+(float)Math.cos(a)*outer,y+(float)Math.sin(a)*outer,p);}
 }
}
