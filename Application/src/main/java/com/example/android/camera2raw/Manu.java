package com.example.android.camera2raw;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Manu extends AppCompatActivity {

    private final String[] FORMAT = {"JPEG","RAW","JPEG+DNG","DNG"};
    private int format_now = 1;
    private final String[] SIZE = {"3968*2976","3968*2240","2976*2976","2048*1536","3264*2448","3264*1840","1920*1080","1280*720","640*480","320*240"};
    private int size_now = 6;
    private final String[] PATH = {"内部存储","OTG存储"};
    private int path_now = 0;
    private String path = "/storage/emulated/0/DCIM";
    private Switch switch_mode;
    private boolean extremeMode = false;
    private long lastButtonClickTime = 0l;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences pref = getSharedPreferences("menudata",MODE_PRIVATE);
        format_now = pref.getInt("format",1);       //RAW
        size_now = pref.getInt("size",6);          //1920*1080
        path_now = pref.getInt("path",0);         //内部存储
        extremeMode = pref.getBoolean("extremeMode",false);         //是否为extreme mode
        Log.i("Menu","读取SharedPreferences  "+FORMAT[format_now]+"  "+SIZE[size_now]+"  "+PATH[path_now]);
        setContentView(R.layout.manu_layout);
        ImageButton formatButtonL = (ImageButton)findViewById(R.id.format_L);
        ImageButton formatButtonR = (ImageButton)findViewById(R.id.format_R);
        ImageButton sizeButtonL = (ImageButton)findViewById(R.id.size_L);
        ImageButton sizeButtonR = (ImageButton)findViewById(R.id.size_R);
        ImageButton pathButtonR = (ImageButton)findViewById(R.id.path_R);
        ImageButton pathButtonL = (ImageButton)findViewById(R.id.path_L);
        Button okButton = (Button)findViewById(R.id.ok);
        final TextView formatText = (TextView)findViewById(R.id.format_text);
        final TextView sizeText = (TextView)findViewById(R.id.size_text);
        final TextView pathText = (TextView)findViewById(R.id.path_text);
        switch_mode = (Switch)findViewById(R.id.switch_mode);
        if(extremeMode)
            switch_mode.setChecked(true);
        else
            switch_mode.setChecked(false);
        formatText.setText(FORMAT[format_now]);
        sizeText.setText(SIZE[size_now]);
        pathText.setText(PATH[path_now]);
        formatButtonL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(format_now > 0)
                    format_now--;
                if(format_now == 1){
                    sizeText.setText(SIZE[6]);
                    size_now = 6;
                }
                else if(format_now == 3){
                    sizeText.setText(SIZE[0]);
                    size_now = 0;
                }
                else
                    sizeText.setText(SIZE[size_now]);
                formatText.setText(FORMAT[format_now]);
            }
        });
        formatButtonR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(format_now < FORMAT.length-1)
                    format_now++;
                if(format_now == 1){
                    sizeText.setText(SIZE[6]);
                    size_now = 6;
                }
                else if(format_now == 3){
                    sizeText.setText(SIZE[0]);
                    size_now = 0;
                }
                else
                    sizeText.setText(SIZE[size_now]);
                formatText.setText(FORMAT[format_now]);
            }
        });
        sizeButtonL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(size_now > 0 && format_now != 1 && format_now != 3)
                    size_now--;
                sizeText.setText(SIZE[size_now]);  //仅改变JPEG格式大小，DNG图片大小无法改变
            }
        });
        sizeButtonR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(size_now < SIZE.length-1 && format_now != 1 && format_now != 3)
                    size_now++;
                sizeText.setText(SIZE[size_now]);   //仅改变JPEG格式大小，DNG图片大小无法改变
            }
        });
        pathButtonL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(path_now >0){
                    path_now--;
                }
                path = "/storage/emulated/0/DCIM";
                pathText.setText(PATH[path_now]);
            }
        });
        pathButtonR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(path_now < 1){
                    path_now++;
                    String s = isHaveOTG("/storage");
                    if (s != null){
                        path = s;
                        pathText.setText(PATH[path_now]);
                    }else {
                        Toast.makeText(MyApplication.getContext(),"没有可用的OTG路径",Toast.LENGTH_SHORT).show();
                        path_now = 0;
                        pathText.setText(PATH[path_now]);
                    }
                }
            }
        });

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        switch_mode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                Log.e("Menu","Switch change!");
                extremeMode = b;
            }
        });

    }
    @Override
    public void onBackPressed(){
        if(System.currentTimeMillis() - lastButtonClickTime > 2000l){           //防止短时间重复按
            lastButtonClickTime = System.currentTimeMillis();
            SharedPreferences.Editor editor = getSharedPreferences("menudata",MODE_PRIVATE).edit();
            editor.clear();
            editor.putInt("format",format_now);
            editor.putInt("size",size_now);
            editor.putInt("path",path_now);
            editor.putBoolean("extremeMode",extremeMode);
            editor.apply();                          //保存菜单数据
            Log.i("Menu","写入SharedPreferences  "+FORMAT[format_now]+"  "+SIZE[size_now]+"  "+PATH[path_now]);

            File file_old = new File("/storage/emulated/0/DCIM/isSaveDng.dat");
            File file_new = new File("/storage/emulated/0/DCIM/isSaveDng.dat");
            DataOutputStream out = null;
            try{
                file_old.delete();  //删除旧文件
                file_old = null;
                out = new DataOutputStream(new FileOutputStream(file_new));
                if(format_now == 0 || format_now == 1)
                    out.writeBoolean(false);
                else
                    out.writeBoolean(true);
            }catch (IOException e){
                e.printStackTrace();
            }finally {
                try{
                    file_new = null;
                    if(out != null)
                        out.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        /*Camera2RawFragment c2rf = new Camera2RawFragment();
        c2rf.C2RF.finish();*/
        /*Intent intent = new Intent();
        intent.putExtra("data_return",format_now);
        intent.putExtra("path_name",path);
        setResult(RESULT_OK,intent);*/
            Intent intent = new Intent(Manu.this,CameraActivity.class);     //重启App
            startActivity(intent);
            finish();
        }
    }
    public static String isHaveOTG(String path){
        File file = new File(path);
        File[] files = file.listFiles();
        if (files == null){
            Log.e("Error","空目录");
            return null;
        }
        /*List<String> s = new ArrayList<>();
        for (int i = 0;i < files.length;i++){
            s.add(files[i].getAbsolutePath());
        }*/
        if (files.length > 2){
            return files[0].getAbsolutePath();
        }
        return null;
    }

    /*private void UpdateText(final int id,final String str){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (id){
                    case R.id.format_text
                }
            }
        });
    }*/
}
