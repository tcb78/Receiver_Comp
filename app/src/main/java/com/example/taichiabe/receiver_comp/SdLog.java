package com.example.taichiabe.receiver_comp;
/*=====================================================================*
http://d.hatena.ne.jp/bs-android/20091001/1254407223
 *=====================================================================*/
import java.io.File;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Date;

import android.os.Environment;
import android.util.Log;

public class SdLog {

    public static final String LOG_DIR = Environment.getExternalStorageDirectory().getPath() + "/ReceiverDebugLog/";

    public static void put(String title, String text) {

        Date now = new Date();
        String sdFile = LOG_DIR + title + ".txt";
        String logText = (now.getYear()+1900)+"/"+(now.getMonth()+1)+"/"+now.getDate()
                +" "+now.getHours()+":"+now.getMinutes()+":"+now.getSeconds()+"  "+text+"\n";
        BufferedWriter bw = null;
        //StackTraceElement[] ste = (new Throwable()).getStackTrace();
        //text = ste[1].getMethodName() + "(" + ste[1].getFileName() + ":" + ste[1].getLineNumber() + ") " + text;
        try {
            try {
                makeDirectory(LOG_DIR);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            FileOutputStream file = new FileOutputStream(sdFile, true);
            bw = new BufferedWriter(new OutputStreamWriter(
                    file, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            bw.append(logText);
            Log.e("log",logText);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        bw = null;
    }

    private static void makeDirectory(File dir) throws IOException {
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("File.mkdirs() failed.");
            }
            //return true;
        } else if (!dir.isDirectory()) {
            throw new IOException("Cannot create path. " + dir.toString() + " already exists and is not a directory.");
        }
    }

    private static void makeDirectory(String dir) throws IOException {
        makeDirectory(new File(dir));
    }
}