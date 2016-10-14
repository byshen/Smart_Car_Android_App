package com.example.am.kc3c.master.util;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

//新建一个类继承View
public class Drawl extends View{
    private int mov_x;//声明起点坐标
    private int mov_y;
    private Paint paint;//声明画笔
    private Canvas canvas;//画布
    private Bitmap bitmap;//位图
    public Drawl(Context context) {
        super(context);
        paint=new Paint(Paint.DITHER_FLAG);//创建一个画笔
        bitmap = Bitmap.createBitmap(800, 1100, Bitmap.Config.ARGB_8888); //设置位图的宽高
        canvas=new Canvas();
        canvas.setBitmap(bitmap);

        paint.setStyle(Paint.Style.STROKE);//设置非填充
        paint.setStrokeWidth(20);//笔宽
        paint.setColor(Color.rgb(200, 200, 255));//设置color
        paint.setAntiAlias(true);//锯齿不显示

    }
    //画位图
    @Override
    protected void onDraw(Canvas canvas) {
//  super.onDraw(canvas);
        canvas.drawBitmap(bitmap,0,0,null);
    }
    //触摸事件
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction()==MotionEvent.ACTION_MOVE) {//如果拖动
            canvas.drawLine(mov_x, mov_y, event.getX(), event.getY(), paint);//画线
            invalidate();
        }
        if (event.getAction()==MotionEvent.ACTION_DOWN) {//如果点击
            mov_x=(int) event.getX();
            mov_y=(int) event.getY();
            canvas.drawPoint(mov_x, mov_y, paint);//画点
            invalidate();
        }
        mov_x=(int) event.getX();
        mov_y=(int) event.getY();
        return true;
    }

}