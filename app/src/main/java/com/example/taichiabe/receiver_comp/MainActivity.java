package com.example.taichiabe.receiver_comp;
/*=========================================================*
 * システム：受信・リアルタイムFFT
 * 周辺周波数成分と比較して検出
 * Receiver Comparison
 *==========================================================*/
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Vibrator;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity implements OnCheckedChangeListener {

    //サンプリングレート
    public static final int SAMPLING_RATE = 44100;
    //FFTのポイント数（2の累乗）
    public static final int FFT_SIZE = 4096;
    //デシベルベースラインの設定
    public static final double DB_BASELINE = Math.pow(2, 15) * FFT_SIZE * Math.sqrt(2);
    //分解能の計算
    public static final double RESOLUTION = SAMPLING_RATE / (double) FFT_SIZE;
    private Vibrator vib;
    private AudioRecord audioRec = null;
    boolean isRecording = false;
    Thread fft;
    private TimeMeasure tm;
    SdLog sdlog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView receivingFreqText = findViewById(R.id.receivingFreqText);
        receivingFreqText.setText(R.string.receivingFreqText);
        TextView decibelDiffText = findViewById(R.id.decibelDiffText);
        decibelDiffText.setText(R.string.decibelDiffText);
        Switch receivingSwitch = findViewById(R.id.receivingSwitch);
        receivingSwitch.setOnCheckedChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

        if(isChecked) {

            EditText receivingFreqEdit = findViewById(R.id.receivingFreqEdit);
            EditText decibelDiffEdit = findViewById(R.id.decibelDiffEdit);

            final int RECEIVING_FREQ = Integer.parseInt(receivingFreqEdit.getText().toString());
            final double DECIBEL_DIFF = Integer.parseInt(decibelDiffEdit.getText().toString());

            final String FILENAME = String.valueOf(System.currentTimeMillis());

            /*
            if(RECVFREQ == 18000)      FFTPOINT = 3344;
            else if(RECVFREQ == 18500) FFTPOINT = 3436;
            else if(RECVFREQ == 19000) FFTPOINT = 3530;
            else if(RECVFREQ == 19500) FFTPOINT = 3622;
            else if(RECVFREQ == 20000) FFTPOINT = 3716;
            else if(RECVFREQ == 20500) FFTPOINT = 3808;
            else if(RECVFREQ == 21000) FFTPOINT = 3900;
            else if(RECVFREQ == 21500) FFTPOINT = 3994;
            else if(RECVFREQ == 22000) FFTPOINT = 4086;
            */

            //実験用デバイスではRecvBufSize = 3584
            final int MIN_BUFFER_SIZE = AudioRecord.getMinBufferSize(
                    SAMPLING_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            final int RECORD_BUFFER_SIZE = MIN_BUFFER_SIZE * 4;

            //最終的にFFTクラスの4096以上を確保するためbufferSizeInBytes = RecvBufSize * 4
            audioRec = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLING_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    RECORD_BUFFER_SIZE);

            //Vibratorクラスのインスタンス取得
            vib = (Vibrator)getSystemService(VIBRATOR_SERVICE);

            //TimeMeasureクラスのインスタンス取得
            tm = new TimeMeasure();

            audioRec.startRecording();
            isRecording = true;

            //フーリエ解析スレッドを生成
            fft = new Thread(new Runnable() {
                @Override
                public void run() {

                    byte[] recordData = new byte[RECORD_BUFFER_SIZE];
                    while (isRecording) {

                        //計測開始
                        tm.startMeasure();

                        audioRec.read(recordData, 0, recordData.length);
                        //エンディアン変換
                        short[] shortData = toLittleEndian(recordData, RECORD_BUFFER_SIZE);
                        //FFTクラスの作成と値の引き出し
                        double[] fftData = fastFourierTransform(shortData);
                        //パワースペクトル・デシベルの計算
                        double[] decibelFrequencySpectrum = computePowerSpectrum(fftData);

                        //比較対象の周波数帯の平均デシベル値を取得
                        int targetDecibelAve = computeComparisonTargetDecibelAve(RECEIVING_FREQ, decibelFrequencySpectrum);

                        //ドップラー効果を考慮した接近検知
                        detectApproaching(RECEIVING_FREQ, targetDecibelAve + DECIBEL_DIFF, decibelFrequencySpectrum, FILENAME);

                        //sdlog.put("20khz2-" + filename, String.format("%.3f", de_start / 2 * resol) + ":" + String.valueOf(dbfs[de_start / 2]) + "  title:" + String.valueOf(total));

                        //計測終了
                        tm.finishMeasure();
                        sdlog.put("time2-" + FILENAME, "process time:" + tm.measureTimeSec().substring(0, 5));
                        //処理時間出力
                        tm.printTimeSec();

                    }
                    audioRec.stop();
                    audioRec.release();
                }

            });
            //スレッドのスタート
            fft.start();

        } else {

            if(audioRec.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                vib.cancel();
                audioRec.stop();
                //audioRec.release();
                isRecording = false;
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if(audioRec.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            audioRec.stop();
            isRecording = false;
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(audioRec.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            audioRec.stop();
            audioRec.release();
            isRecording = false;
        }
    }

    /**
     * エンディアン変換
     * @param   buf   受信バッファデータ
     * @param   bufferSize  受信バッファサイズ
     * @return  shortData   エンディアン変換後short型データ
     */
    public short[] toLittleEndian(byte[] buf, int bufferSize) {

        //エンディアン変換
        //配列bufをもとにByteBufferオブジェクトbfを作成
        ByteBuffer bf = ByteBuffer.wrap(buf);
        //バッファをクリア（データは削除されない）
        bf.clear();
        //リトルエンディアンに変更
        bf.order(ByteOrder.LITTLE_ENDIAN);
        short[] shortData = new short[bufferSize / 2];
        //位置から容量まで
        for (int i = bf.position(); i < bf.capacity() / 2; i++) {
            //short値を読むための相対getメソッド
            //現在位置の2バイトを読み出す
            shortData[i] = bf.getShort();
        }
        return shortData;
    }

    /**
     * 高速フーリエ変換
     * @param   shortData   エンディアン変換後データ
     * @return  fftData     フーリエ変換後データ
     */
    public double[] fastFourierTransform(short[] shortData) {

        //FFTクラスの作成と値の引き渡し
        FFT4g fft = new FFT4g(FFT_SIZE);
        double[] fftData = new double[FFT_SIZE];
        for(int i = 0; i < FFT_SIZE; i++) {
            fftData[i] = (double) shortData[i];
        }
        fft.rdft(1, fftData);

        return fftData;
    }

    /**
     * パワースペクトル・デシベルの計算
     * @param   fftData         フーリエ変換後のデータ
     * @return  decibelFrequencySpectrum    デシベル値
     */
    public double[] computePowerSpectrum(double[] fftData) {

        //パワースペクトル・デシベルの計算
        double[] powerSpectrum = new double[FFT_SIZE / 2];
        //DeciBel Frequency Spectrum
        double[] decibelFrequencySpectrum = new double[FFT_SIZE / 2];
        for(int i = 0; i < FFT_SIZE; i += 2) {
            //dbfs[i / 2] = (int) (20 * Math.log10(Math.sqrt(Math.pow(FFTdata[i], 2) + Math.pow(FFTdata[i + 1], 2)) / dB_baseline));
            powerSpectrum[i / 2] = Math.sqrt(Math.pow(fftData[i], 2) + Math.pow(fftData[i + 1], 2));
            decibelFrequencySpectrum[i / 2] = (int) (20 * Math.log10(powerSpectrum[i / 2] / DB_BASELINE));
        }
        return decibelFrequencySpectrum;
    }

    /**
     * 比較対象の周波数帯の平均デシベル値を取得
     * @param freq      受信周波数
     * @param decibelFrequencySpectrum  デシベル値周波数成分
     * @return  targetDecibelAve    比較対象の周波数帯の平均デシベル値
     */
    public int computeComparisonTargetDecibelAve(int freq, double[] decibelFrequencySpectrum) {

        //比較対象の周波数を設定 comparison target
        int targetDecibelAve = 0, freqFrameIter = 0;
        int ctFirstFrame = frameSetting(freq - 2000);
        int ctLastFrame = frameSetting(freq - 1500);

        for (int j = ctFirstFrame / 2; j < ctLastFrame / 2; j++) {
            targetDecibelAve += decibelFrequencySpectrum[j];
            freqFrameIter++;
        }
        try {
            targetDecibelAve /= freqFrameIter;
        } catch (ArithmeticException e) {
            e.printStackTrace();
        }
        return targetDecibelAve;
    }

    /**
     * 接近検知アルゴリズム
     * @param freq          受信周波数
     * @param decibel       検知デシベル値
     * @param decibelFrequencySpectrum  デシベル値周波数成分
     * @param filename      ファイル名
     */
    public void detectApproaching(int freq, double decibel, double[] decibelFrequencySpectrum, String filename) {

        //ドップラー効果考慮 Doppler Effect
        //500Hzの幅で接近検知
        int deFirstFrame = frameSetting(freq);
        int deLastFrame = frameSetting(freq + 500);

        for(int j = deFirstFrame / 2; j < deLastFrame / 2; j++) {
            if(decibelFrequencySpectrum[j] > decibel) {
                //検知周波数
                sdlog.put("freq2-" + filename, String.format(Locale.US, "%.3f", j * RESOLUTION)
                                                                + ":" + String.valueOf(decibelFrequencySpectrum[j]) + "  title:" + String.valueOf(decibel));
                //インスタンス取得が外だから振動しない可能性あり
                vib.cancel();
                vib.vibrate(200);
                break;
            }
        }
    }

    /**
     * 周波数からフレーム設定
     * @param   freq    周波数
     * @return  frame   フレーム
     */
    public int frameSetting(int freq) {

        int frame = (int)(2 * freq / RESOLUTION);
        if(frame % 2 == 1) {
            frame++;
        }
        return frame;
    }
}