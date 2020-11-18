package com.example.android.camera2raw;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
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
    private String[] SIZE = {"3968*2976","3968*2240","3264*2448","3264*1840","2976*2976","2048*1536","1920*1080","1280*720","640*480","320*240"};
    private int size_now = 0;
    private final String[] PATH = {"内部存储","OTG存储"};
    private int path_now = 0;
    private String path = "/storage/emulated/0/DCIM";
    private Switch switch_mode;
    private boolean extremeMode = false;
    private long lastButtonClickTime = 0l;
    private int[] rawSizeRange;
    private int rawSizeIndex = 0;
    private int realDepth = 0;
    private int bitDepth_now = 16;
    private int interval = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.manu_layout);
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
            actionBar.hide();
        final TextView depthText = (TextView)findViewById(R.id.text_depth);
        final TextView bitDepthText = (TextView)findViewById(R.id.bitDepth_text);
        Intent intent = getIntent();
        SIZE = intent.getStringArrayExtra("availableJPEGSize");      //上一个活动的数组
        rawSizeRange = intent.getIntArrayExtra("rawSizeArray");
        realDepth = intent.getIntExtra("realPixelDepth",0);
        interval = intent.getIntExtra("Interval",1);
        SharedPreferences pref = getSharedPreferences("menudata",MODE_PRIVATE);
        format_now = pref.getInt("format",1);       //RAW
        size_now = pref.getInt("size",0);          //1920*1080
        path_now = pref.getInt("path",0);         //内部存储
        extremeMode = pref.getBoolean("extremeMode",false);         //是否为extreme mode
        rawSizeIndex = pref.getInt("rawSizeArrayIndex",0);
        bitDepth_now = pref.getInt("RawBitDepth",16);
        if(realDepth == 0)
            realDepth = pref.getInt("realDepth",0);
        if(realDepth == 0)
            depthText.setText("Camera output effective bit depth相机输出有效位深: unknow");
        else
            depthText.setText("Camera output effective bit depth相机输出有效位深: "+Integer.toString(realDepth));
        //Log.i("Menu","读取SharedPreferences  "+FORMAT[format_now]+"  "+SIZE[size_now]+"  "+PATH[path_now]);
        ImageButton formatButtonL = (ImageButton)findViewById(R.id.format_L);
        ImageButton formatButtonR = (ImageButton)findViewById(R.id.format_R);
        ImageButton sizeButtonL = (ImageButton)findViewById(R.id.size_L);
        ImageButton sizeButtonR = (ImageButton)findViewById(R.id.size_R);
        ImageButton pathButtonR = (ImageButton)findViewById(R.id.path_R);
        ImageButton pathButtonL = (ImageButton)findViewById(R.id.path_L);
        ImageButton bitDepthButtonR = (ImageButton)findViewById(R.id.bitDepth_R);
        ImageButton bitDepthButtonL = (ImageButton)findViewById(R.id.bitDepth_L);
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
        if(format_now == 1){
            bitDepthText.setText(Integer.toString(bitDepth_now));
            if(rawSizeIndex == rawSizeRange.length-1 && rawSizeRange[rawSizeIndex] == 1920)
                sizeText.setText("1920*1080");
            else
                sizeText.setText(Integer.toString(rawSizeRange[rawSizeIndex])+"*"+Integer.toString(rawSizeRange[rawSizeIndex]/4*3));
        }
        else if (format_now == 3){
            sizeText.setText(SIZE[0]);
            bitDepthText.setText(R.string.sixteen);
        }
        else if (format_now == 0){
            sizeText.setText(SIZE[size_now]);
            bitDepthText.setText(R.string.eight);
        }
        else {
            sizeText.setText(SIZE[size_now]);
            bitDepthText.setText("8 + 16");
        }
        pathText.setText(PATH[path_now]);

        formatButtonL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(format_now > 0)
                    format_now--;
                if(format_now == 1){         //RAW
                    if(rawSizeIndex == rawSizeRange.length-1 && rawSizeRange[rawSizeIndex] == 1920)
                        sizeText.setText("1920*1080");
                    else
                        sizeText.setText(Integer.toString(rawSizeRange[rawSizeIndex])+"*"+Integer.toString(rawSizeRange[rawSizeIndex]/4*3));
                    bitDepthText.setText(Integer.toString(bitDepth_now));
                }
                else if(format_now == 3){      //DNG
                    sizeText.setText(SIZE[0]);
                    bitDepthText.setText(R.string.sixteen);
                }
                else if (format_now == 0){
                    sizeText.setText(SIZE[size_now]);
                    bitDepthText.setText(R.string.eight);
                }
                else {
                    sizeText.setText(SIZE[size_now]);
                    bitDepthText.setText("8 + 16");
                }
                formatText.setText(FORMAT[format_now]);
            }
        });

        formatButtonR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(format_now < FORMAT.length-1)
                    format_now++;
                if(format_now == 1){
                    if(rawSizeIndex == rawSizeRange.length-1 && rawSizeRange[rawSizeIndex] == 1920)
                        sizeText.setText("1920*1080");
                    else
                        sizeText.setText(Integer.toString(rawSizeRange[rawSizeIndex])+"*"+Integer.toString(rawSizeRange[rawSizeIndex]/4*3));
                    bitDepthText.setText(Integer.toString(bitDepth_now));
                }
                else if(format_now == 3){
                    sizeText.setText(SIZE[0]);
                    bitDepthText.setText(R.string.sixteen);
                }
                else if (format_now == 0){
                    sizeText.setText(SIZE[size_now]);
                    bitDepthText.setText(R.string.eight);
                }
                else {
                    sizeText.setText(SIZE[size_now]);
                    bitDepthText.setText("8 + 16");
                }
                formatText.setText(FORMAT[format_now]);
            }
        });

        sizeButtonL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(size_now > 0 && format_now != 1 && format_now != 3) {
                    size_now--;
                    sizeText.setText(SIZE[size_now]);  //仅改变JPEG格式大小，DNG图片大小无法改变
                }
                else if (format_now == 1 && rawSizeIndex > 0){       //RAW格式大小修改
                    rawSizeIndex--;
                    sizeText.setText(Integer.toString(rawSizeRange[rawSizeIndex])+"*"+Integer.toString(rawSizeRange[rawSizeIndex]/4*3));
                }
            }
        });

        sizeButtonR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(size_now < SIZE.length-1 && format_now != 1 && format_now != 3){
                    size_now++;
                    sizeText.setText(SIZE[size_now]);   //仅改变JPEG格式大小，DNG图片大小无法改变
                }
                else if (format_now == 1 && rawSizeIndex < rawSizeRange.length-1){         //RAW格式大小修改
                    rawSizeIndex++;
                    if(rawSizeIndex == rawSizeRange.length-1  && rawSizeRange[rawSizeIndex] == 1920)
                        sizeText.setText("1920*1080");
                    else
                        sizeText.setText(Integer.toString(rawSizeRange[rawSizeIndex])+"*"+Integer.toString(rawSizeRange[rawSizeIndex]/4*3));
                }
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
                        //Toast.makeText(MyApplication.getContext(),"没有可用的OTG路径",Toast.LENGTH_SHORT).show();
                        path_now = 0;
                        pathText.setText(PATH[path_now]);
                    }
                }
            }
        });

        bitDepthButtonL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(bitDepth_now == 16 && format_now == 1){
                    bitDepth_now = 12;
                    bitDepthText.setText(R.string.twelve);
                }
                else if (bitDepth_now == 12 && format_now == 1){
                    bitDepth_now = 10;
                    bitDepthText.setText(R.string.ten);
                }
                else if (bitDepth_now == 10 && format_now == 1){
                    bitDepth_now = 8;
                    bitDepthText.setText(R.string.eight);
                }
            }
        });

        bitDepthButtonR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(bitDepth_now == 8 && format_now == 1){
                    bitDepth_now = 10;
                    bitDepthText.setText(R.string.ten);
                }
                else if (bitDepth_now == 10 && format_now == 1){
                    bitDepth_now = 12;
                    bitDepthText.setText(R.string.twelve);
                }
                else if (bitDepth_now == 12 && format_now == 1){
                    bitDepth_now = 16;
                    bitDepthText.setText(R.string.sixteen);
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
            editor.putInt("rawSizeArrayIndex",rawSizeIndex);
            editor.putInt("realDepth",realDepth);
            editor.putInt("RawBitDepth",bitDepth_now);
            editor.putInt("IntervalTime",interval);
            editor.apply();                          //保存菜单数据
            //Log.i("Menu","写入SharedPreferences  "+FORMAT[format_now]+"  "+SIZE[size_now]+"  "+PATH[path_now]);

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
