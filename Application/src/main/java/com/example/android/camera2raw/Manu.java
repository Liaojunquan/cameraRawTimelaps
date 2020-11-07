package com.example.android.camera2raw;

import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Manu extends AppCompatActivity {

    private final String[] FORMAT = {"JPEG","RAW","JPEG+RAW"};
    private int format_now = 1;
    private final String[] SIZE = {"3968*2976","3968*2240","2976*2976","2048*1536","3264*2448","3264*1840","1920*1080","1280*720","640*480","320*240"};
    private int size_now = 0;
    private final String[] PATH = {"内部存储","OTG存储"};
    private int path_now = 0;
    private String path = "/storage/emulated/0/DCIM";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manu_layout);
        Button formatButtonL = (Button)findViewById(R.id.format_L);
        Button formatButtonR = (Button)findViewById(R.id.format_R);
        Button sizeButtonL = (Button)findViewById(R.id.size_L);
        Button sizeButtonR = (Button)findViewById(R.id.size_R);
        Button pathButtonR = (Button)findViewById(R.id.path_R);
        Button pathButtonL = (Button)findViewById(R.id.path_L);
        final TextView formatText = (TextView)findViewById(R.id.format_text);
        final TextView sizeText = (TextView)findViewById(R.id.size_text);
        final TextView pathText = (TextView)findViewById(R.id.path_text);
        formatButtonL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(format_now > 0)
                    format_now--;
                formatText.setText(FORMAT[format_now]);
            }
        });
        formatButtonR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(format_now < FORMAT.length-1)
                    format_now++;
                formatText.setText(FORMAT[format_now]);
            }
        });
        sizeButtonL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(size_now > 0)
                    size_now--;
                sizeText.setText(SIZE[size_now]);
            }
        });
        sizeButtonR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(size_now < SIZE.length-1)
                    size_now++;
                sizeText.setText(SIZE[size_now]);
            }
        });
        pathButtonL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(path_now >0){
                    //path_now--;
                }
                path = "/storage/emulated/0/DCIM";
                pathText.setText(PATH[path_now]);
            }
        });
        pathButtonR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(path_now < 1){
                    //path_now++;
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
    }
    @Override
    public void onBackPressed(){
        Intent intent = new Intent();
        intent.putExtra("data_return",format_now);
        intent.putExtra("path_name",path);
        setResult(RESULT_OK,intent);
        finish();
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
}
