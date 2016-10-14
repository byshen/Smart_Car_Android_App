package com.example.am.kc3c.master.menu;

import android.view.MenuItem;

import com.example.am.kc3c.master.R;

/**
 * Created by am on 2015/4/13.
 * 除MainActivity之外都是离开即销毁
 *
 * 弃用，后期的activity都是单例模式，没必要使用
 * 再次启用，VideoActivity保留view会浪费大量CPU和内存资源
 */
public class ActivityStandard extends ActivityMenuGroup {
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean flag = super.onOptionsItemSelected(item);
        if (item.getItemId()!= R.id.action_main) {
            this.finish();
        }
        return flag;
    }
}
