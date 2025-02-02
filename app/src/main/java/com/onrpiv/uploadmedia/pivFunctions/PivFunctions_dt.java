package com.onrpiv.uploadmedia.pivFunctions;

import android.os.Environment;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import static org.opencv.core.Core.mean;
import static org.opencv.core.Core.subtract;
import static org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2GRAY;
import static org.opencv.imgproc.Imgproc.cvtColor;

/**
 * author: sarbajit mukherjee
 * Created by sarbajit mukherjee on 09/07/2020.
 */

public class PivFunctions_dt {
    private int windowSize;
    private int overlap;
    private double dt;
    private String sig2noise_method;
    private String frame1;
    private String frame2;

    public PivFunctions_dt(String imagePath1,
                           String imagePath2,
                           int mWindow_size,
                           int mOverlap,
                           double mDt,
                           String mSig2noise_method){

        frame1 = imagePath1;
        frame2 = imagePath2;
        windowSize = mWindow_size;
        overlap = mOverlap;
        dt = mDt;
        sig2noise_method = mSig2noise_method;
    }

    private Map<String, Integer> getFieldShape(int imgCols, int imgRows, int areaSize, int overlap){
        int nRows = ((imgRows - areaSize) / (areaSize - overlap) + 1);
        int nCols = ((imgCols - areaSize) / (areaSize - overlap) + 1);
        Map<String,Integer> map =new HashMap();
        map.put("nRows",nRows);
        map.put("nCols",nCols);
        return map;

    }

    private Mat openCvPIV(Mat image, Mat temp) {
        int P = temp.rows();
        int Q = temp.cols();

        int M = image.rows();
        int N = image.cols();

        Mat Xt = Mat.zeros(M+2*(P-1), N+2*(Q-1), CvType.CV_8U);
        Rect rect = new Rect((P-1), (Q-1), image.width(), image.height());
        Mat submat= Xt.submat(rect);
        image.copyTo(submat);

        Mat outputCorr=new Mat();
        Imgproc.matchTemplate(Xt, temp, outputCorr, Imgproc.TM_CCORR);
        return outputCorr;
    }

    private Map<String, Double> sig2Noise_update(Mat corr) {
        Core.MinMaxLocResult mmr = Core.minMaxLoc(corr);

        int peak1_i = (int) mmr.maxLoc.x;
        int peak1_j = (int) mmr.maxLoc.y;
        double peak1_value = mmr.maxVal;

        corr.put(peak1_j, peak1_i, 0.0);

        Core.MinMaxLocResult mmr2 = Core.minMaxLoc(corr);
        double peak2_value = mmr2.maxVal;

        double sig2Noise = peak1_value / peak2_value;

        Map<String, Double> map =new HashMap();
        map.put("sig2Noise",sig2Noise);
        return map;
    }


    public Map<String, double[][]> extendedSearchAreaPiv_update(){
        int i1t, j1l;
        int i2t, j2l;

        int search_area_size = windowSize;

        Mat image1 = Imgcodecs.imread(frame1);
        Mat image2 = Imgcodecs.imread(frame2);

        cvtColor(image1, image1, COLOR_BGR2GRAY);
        cvtColor(image2, image2, COLOR_BGR2GRAY);

        //get field shape
        Map<String, Integer> fieldShape = getFieldShape(image1.cols(), image1.rows(), search_area_size, overlap);

        double[][] dr1 = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];
        double[][] dc1 = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];

        double[][] eps_r = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];
        double[][] eps_c = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];

        double[][] mag = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];
        double[][] sig2noise = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];


        Mat corr;
        double win1_avg;
        double win2_avg;

        int nr= fieldShape.get("nRows");
        int nc = fieldShape.get("nCols");

        for (int i = 0; i < nr; i++) {
            for (int j = 0; j < nc; j++) {

                Mat window_a_1 = Mat.zeros(64, 64, CvType.CV_8U);
                Mat window_b_1 = Mat.zeros(64, 64, CvType.CV_8U);

                i1t = 0 + overlap*(i+1) - overlap;

                j1l = 0 + overlap*(j+1) - overlap;

//                Log.d("WINDOWS: ", (i1t+1)+":"+(i1t+windowSize)+" ,"+(j1l+1)+":"+(j1l+windowSize));

                Rect rectWin_a = new Rect(j1l, i1t, windowSize, windowSize);
                Mat window_a = new Mat(image1, rectWin_a);

                i2t = 0 + overlap*(i+1) - overlap;

                j2l = 0 + overlap*(j+1) - overlap;

                Rect rectWin_b = new Rect(j2l, i2t, windowSize, windowSize);
                Mat window_b = new Mat(image2, rectWin_b);

                win1_avg = mean(window_a).val[0];
                win2_avg = mean(window_b).val[0];

                subtract(window_a, new Scalar(win1_avg), window_a_1);
                subtract(window_b, new Scalar(win2_avg), window_b_1);

                corr = openCvPIV(window_a_1, window_b_1);
                Core.MinMaxLocResult mmr = Core.minMaxLoc(corr);

                int c = (int) mmr.maxLoc.x;
                int r = (int) mmr.maxLoc.y;

//                Log.d("WINDOWS: ", "i: "+i+" j:"+j);
                try {
                    eps_r[i][j] = (Math.log(corr.get(r-1,c)[0]) - Math.log(corr.get(r+1,c)[0])) / (2 * (Math.log(corr.get(r-1,c)[0]) -  2*Math.log(corr.get(r,c)[0]) + Math.log(corr.get(r+1,c)[0])));
                    eps_c[i][j] = (Math.log(corr.get(r,c-1)[0]) - Math.log(corr.get(r,c+1)[0])) / (2 * (Math.log(corr.get(r,c-1)[0]) - 2*Math.log(corr.get(r,c)[0]) + Math.log(corr.get(r,c+1)[0])));

                    dr1[i][j] = ((windowSize-1) - (r + eps_r[i][j]))/dt;
                    dc1[i][j] = ((windowSize-1) - (c + eps_c[i][j]))/dt;
                } catch (Exception e) {
                    dr1[i][j] = 0.0;
                    dc1[i][j] = 0.0;
                }
                mag[i][j] = Math.sqrt(Math.pow(dr1[i][j], 2) + Math.pow(dc1[i][j], 2));

                Map<String, Double> sig2NoiseRatio = sig2Noise_update(corr);
                sig2noise[i][j] = sig2NoiseRatio.get("sig2Noise");
            }
        }

        Map<String, double[][]> map =new HashMap();
        map.put("u",dc1);
        map.put("v",dr1);
        map.put("magnitude",mag);
        map.put("sig2Noise",sig2noise);
        return map;
    }

    public Map<String, double[]> getCoordinates(){
        Mat image1 = Imgcodecs.imread(frame1);
        cvtColor(image1, image1, COLOR_RGB2GRAY);
        Map<String, Integer> fieldShape = getFieldShape(image1.cols(), image1.rows(), windowSize, overlap);

        double[] x = new double[fieldShape.get("nCols")];
        double[] y = new double[fieldShape.get("nRows")];
        double[][] yx;

        for (int i = 0; i < fieldShape.get("nCols"); i++) {
            x[i] = i * (windowSize - overlap) + (windowSize) / 2.0;
        }

        for (int j = 0; j < fieldShape.get("nRows"); j ++) {
            y[j] = j * (windowSize - overlap) + (windowSize) / 2.0;
        }

        Map<String, double[]> map =new HashMap();
        map.put("x",x);
        map.put("y",y);
        return map;
    }

    private void saveToFile(String data, String userName, String stepName, String imgFileSaveName){
        try {
            File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/Save_Output_" + userName);
            // Then we create the storage directory if does not exists
            if (!storageDirectory.exists()) storageDirectory.mkdir();
            File txtFile = new File(storageDirectory, stepName + "_"+imgFileSaveName+".txt");

            FileOutputStream fileOutputStream = new FileOutputStream(txtFile,true);
            fileOutputStream.write((data + System.getProperty("line.separator")).getBytes());
        }  catch(FileNotFoundException ex) {
            Log.d("", ex.getMessage());
        }  catch(IOException ex) {
            Log.d("", ex.getMessage());
        }
    }

    public void saveVector(Map<String, double[][]> pivCorrelation, Map<String, double[]> interrCenters, String userName, String stepName, String imgFileSaveName) {
        double ux, vy, q, x, y;
        ArrayList<String> toPrint = new ArrayList<>();

        for (int i = 0; i < interrCenters.get("y").length; i++) {
            for (int j = 0; j < interrCenters.get("x").length; j++) {
                x = interrCenters.get("x")[j];
                y = interrCenters.get("y")[i];
                ux = pivCorrelation.get("u")[i][j]*dt;
                vy = pivCorrelation.get("v")[i][j]*dt;
                q = pivCorrelation.get("sig2Noise")[i][j];

                toPrint.add(String.valueOf(x));
                toPrint.add(String.valueOf(y));
                toPrint.add(String.valueOf(ux));
                toPrint.add(String.valueOf(vy));
                toPrint.add(String.valueOf(q));

                StringJoiner sj1 = new StringJoiner(",  ");
                sj1.add(toPrint.get(0)).add(toPrint.get(1)).add(toPrint.get(2)).add(toPrint.get(3)).add(toPrint.get(4));
                saveToFile(sj1.toString(), userName, stepName, imgFileSaveName);
                toPrint.clear();
//                Log.d("TEXT: ", "y: "+y+" x: "+x+" ux: "+ux+" vy: "+vy+" q: "+q);
//                Log.d("JOIN: ", "string join: "+ sj1.toString());
            }
        }
        int p = 2;
    }

    public void drawArrowsOnImage(Map<String, double[][]> pivCorrelation, Map<String, double[]> interrCenters, String userName, String stepName, String imgFileSaveName){
        Mat image1 = Imgcodecs.imread(frame1);

        int lineType = 8;
        int thickness = 2;
        double tipLength = 0.2;
        double dx, dy;
        Point startPoint = null, endPoint =null;

        for (int i = 0; i < interrCenters.get("y").length; i++) {
            for (int j = 0; j < interrCenters.get("x").length; j++) {
                dx = pivCorrelation.get("u")[i][j];
                dy = -pivCorrelation.get("v")[i][j];

                if (dx < 0 && dy > 0){
                    startPoint = new Point(interrCenters.get("x")[j], interrCenters.get("y")[i]);
                    endPoint = new Point((interrCenters.get("x")[j] - Math.abs(dx)),
                            (interrCenters.get("y")[i] - Math.abs(dy)));
                }
                else if (dx > 0 && dy > 0) {
                    startPoint = new Point(interrCenters.get("x")[j], interrCenters.get("y")[i]);
                    endPoint = new Point((interrCenters.get("x")[j] + Math.abs(dx)),
                            (interrCenters.get("y")[i] - Math.abs(dy)));
                }
                else if (dx > 0 && dy < 0) {
                    startPoint = new Point(interrCenters.get("x")[j], interrCenters.get("y")[i]);
                    endPoint = new Point((interrCenters.get("x")[j] + Math.abs(dx)),
                            (interrCenters.get("y")[i] + Math.abs(dy)));
                }
                else if (dx < 0 && dy < 0) {
                    startPoint = new Point(interrCenters.get("x")[j], interrCenters.get("y")[i]);
                    endPoint = new Point((interrCenters.get("x")[j] - Math.abs(dx)),
                            (interrCenters.get("y")[i] + Math.abs(dy)));
                }
                else if (dx==0 && dy < 0){
                    startPoint = new Point(interrCenters.get("x")[j], interrCenters.get("y")[i]);
                    endPoint = new Point(interrCenters.get("x")[j],   (interrCenters.get("y")[i] + Math.abs(dy)));
                }
                else if (dx==0 && dy > 0) {
                    startPoint = new Point(interrCenters.get("x")[j], interrCenters.get("y")[i]);
                    endPoint = new Point(interrCenters.get("x")[j],   (interrCenters.get("y")[i] - Math.abs(dy)));
                }
                else if (dx < 0 && dy==0){
                    startPoint = new Point(interrCenters.get("x")[j], interrCenters.get("y")[i]);
                    endPoint = new Point((interrCenters.get("x")[j] - Math.abs(dx)), interrCenters.get("y")[i]);
                }
                else if (dx >0 && dy==0){
                    startPoint = new Point(interrCenters.get("x")[j], interrCenters.get("y")[i]);
                    endPoint = new Point((interrCenters.get("x")[j] + Math.abs(dx)), interrCenters.get("y")[i]);
                }
                else if (dx==0 && dy==0){
                    startPoint = new Point(interrCenters.get("x")[j], interrCenters.get("y")[i]);
                    endPoint = new Point(interrCenters.get("x")[j], interrCenters.get("y")[i]);
                }

//                Log.d("-", "----------------------------------------------------------");
                Imgproc.arrowedLine(image1, startPoint, endPoint, new Scalar(66,66, 245), thickness, lineType, 0, tipLength);
            }
        }
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/Save_Output_" + userName);

        // Then we create the storage directory if does not exists
        if (!storageDirectory.exists()) storageDirectory.mkdir();
        File pngFile = new File(storageDirectory, stepName+"_"+imgFileSaveName);

        Imgcodecs.imwrite(pngFile.getAbsolutePath(), image1);
//        int x= 2;
    }

    private double findMedian(double a1, double a2, double a3, double a4, double a5, double a6, double a7, double a8)
    {
        double[] a = new double[8];
        a[0] = a1;
        a[1] = a2;
        a[2] = a3;
        a[3] = a4;
        a[4] = a5;
        a[5] = a6;
        a[6] = a7;
        a[7] = a8;

        int n = a.length;
        // First we sort the array
        Arrays.sort(a);

        // check for even case
        if (n % 2 != 0) {
            return a[n / 2];
        }
        return (a[(n - 1) / 2] + a[n / 2]) / 2.0;
    }

    public Map<String, double[][]> vectorPostProcessing(Map<String, double[][]> pivCorrelation, int mMax, double qMin, double E){
        Mat image1 = Imgcodecs.imread(frame1);
        Map<String, Integer> fieldShape = getFieldShape(image1.cols(), image1.rows(), windowSize, overlap);

        double[][] dr1_p = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];
        double[][] dc1_p = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];

        double sm_r = 0.0, sm_c = 0.0, rm_r = 0.0, rm_c = 0.0, sigma_s_r = 0.0, sigma_s_c = 0.0, r_r = 0.0, r_c = 0.0;

        int nr= fieldShape.get("nRows");
        int nc = fieldShape.get("nCols");

        for (int k = 1; k < nr-1; k++) {
            for (int l = 1; l < nc-1; l++) {

                sm_r = findMedian(pivCorrelation.get("v")[k-1][l-1], pivCorrelation.get("v")[k-1][l],
                        pivCorrelation.get("v")[k-1][l+1], pivCorrelation.get("v")[k][l-1],
                        pivCorrelation.get("v")[k][l+1],   pivCorrelation.get("v")[k+1][l-1],
                        pivCorrelation.get("v")[k+1][l],   pivCorrelation.get("v")[k+1][l+1]);

                sm_c = findMedian(pivCorrelation.get("u")[k-1][l-1], pivCorrelation.get("u")[k-1][l],
                        pivCorrelation.get("u")[k-1][l+1], pivCorrelation.get("u")[k][l-1],
                        pivCorrelation.get("u")[k][l+1],   pivCorrelation.get("u")[k+1][l-1],
                        pivCorrelation.get("u")[k+1][l],   pivCorrelation.get("u")[k+1][l+1]);

                rm_r = findMedian(Math.abs(pivCorrelation.get("v")[k-1][l-1] - sm_r),   Math.abs(pivCorrelation.get("v")[k-1][l] - sm_r),
                                  Math.abs(pivCorrelation.get("v")[k-1][l+1] - sm_r),   Math.abs(pivCorrelation.get("v")[k][l-1] - sm_r),
                                  Math.abs(pivCorrelation.get("v")[k][l+1] - sm_r),     Math.abs(pivCorrelation.get("v")[k+1][l-1] - sm_r),
                                  Math.abs(pivCorrelation.get("v")[k+1][l] -sm_r),      Math.abs(pivCorrelation.get("v")[k+1][l+1] - sm_r));

                rm_c = findMedian(Math.abs(pivCorrelation.get("u")[k-1][l-1] - sm_c),   Math.abs(pivCorrelation.get("u")[k-1][l] - sm_c),
                        Math.abs(pivCorrelation.get("u")[k-1][l+1] - sm_c),   Math.abs(pivCorrelation.get("u")[k][l-1] - sm_c),
                        Math.abs(pivCorrelation.get("u")[k][l+1] - sm_c),     Math.abs(pivCorrelation.get("u")[k+1][l-1] - sm_c),
                        Math.abs(pivCorrelation.get("u")[k+1][l] -sm_c),      Math.abs(pivCorrelation.get("u")[k+1][l+1] - sm_c));

                //Normalization factor
                sigma_s_r = rm_r + 0.1;
                sigma_s_c = rm_c + 0.1;

                //absolute deviation of pixel displacement with respect to the media pixel displacement of the 8 nearest neighbors
                r_r = Math.abs(pivCorrelation.get("v")[k][l] - sm_r) / sigma_s_r;
                r_c = Math.abs(pivCorrelation.get("u")[k][l] - sm_c) / sigma_s_c;

                if (pivCorrelation.get("magnitude")[k][l]*dt < mMax && pivCorrelation.get("sig2Noise")[k][l] > qMin && r_r < E && r_c < E){
                    dr1_p[k][l] = pivCorrelation.get("v")[k][l];
                    dc1_p[k][l] = pivCorrelation.get("u")[k][l];
                }
                else {
                    dr1_p[k][l] = 0.0;
                    dc1_p[k][l] = 0.0;
                }
            }
        }

        Map<String, double[][]> map =new HashMap();
        map.put("u",dc1_p);
        map.put("v",dr1_p);
        map.put("sig2Noise",pivCorrelation.get("sig2Noise"));
        return map;
    }

    public Map<String, double[][]> calculateMultipass(Map<String, double[][]> pivCorrelation, Map<String, double[]> interrCenters){
        Mat image1 = Imgcodecs.imread(frame1);
        Mat image2 = Imgcodecs.imread(frame2);

        cvtColor(image1, image1, COLOR_BGR2GRAY);
        cvtColor(image2, image2, COLOR_BGR2GRAY);

        int search_area_size = windowSize;
        //get field shape
        Map<String, Integer> fieldShape = getFieldShape(image1.cols(), image1.rows(), search_area_size, overlap);

        double[][] dr_new = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];
        double[][] dc_new = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];

        double[][] dr2 = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];
        double[][] dc2 = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];

        double[][] eps_r_new = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];
        double[][] eps_c_new = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];

        double[][] mag = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];
        double[][] sig2noise = new double[fieldShape.get("nRows")][fieldShape.get("nCols")];


        Mat corr;
        int nr= fieldShape.get("nRows");
        int nc = fieldShape.get("nCols");

        for (int ii = 1; ii < nr-1; ii++) {
            for (int jj = 1; jj < nc-1; jj++) {
                Mat window_a_1 = Mat.zeros(64, 64, CvType.CV_8U);
                Mat window_b_1 = Mat.zeros(64, 64, CvType.CV_8U);
                //if pixel displacements from 1st pass are zero keep them as zero in 2nd phase
                if (pivCorrelation.get("v")[ii][jj]==0 && pivCorrelation.get("u")[ii][jj]==0){
                    dr2[ii][jj]=0.0;
                    dc2[ii][jj]=0.0;
                    sig2noise[ii][jj] = 0.0;
                }
                else { //vectors are good
                    //subtract/add half the pixel displacement from interrogation region center
                    //to  find the center of the new interrogation region based on the direction
                    //of the pixel displacement
                    double IA1_x_int = interrCenters.get("x")[jj] - ((pivCorrelation.get("u")[ii][jj]*dt)/2);
                    double IA1_y_int = interrCenters.get("y")[ii] - ((pivCorrelation.get("v")[ii][jj]*dt)/2);

                    double IA2_x_int = interrCenters.get("x")[jj] + ((pivCorrelation.get("u")[ii][jj]*dt)/2);
                    double IA2_y_int = interrCenters.get("y")[ii] + ((pivCorrelation.get("v")[ii][jj]*dt)/2);

                    //Interrogation window for Image 1
                    int IA1_x_s = (int) Math.round((IA1_x_int - (windowSize/2)+1));
                    int IA1_x_e = Math.round(IA1_x_s+windowSize-1);

                    int IA1_y_s = (int) Math.round((IA1_y_int - (windowSize/2)+1));
                    int IA1_y_e = Math.round(IA1_y_s+windowSize-1);

                    Rect rectWin_a = new Rect((IA1_x_s-1), (IA1_y_s-1), windowSize, windowSize);
                    Mat IA1_new_t = new Mat(image1, rectWin_a);

                    //Interrogation window for Image 2
                    int IA2_x_s = (int) Math.round((IA2_x_int - (windowSize/2)+1));
                    int IA2_x_e = Math.round(IA2_x_s+windowSize-1);

                    int IA2_y_s = (int) Math.round((IA2_y_int - (windowSize/2)+1));
                    int IA2_y_e = Math.round(IA2_y_s+windowSize-1);

                    Rect rectWin_b = new Rect((IA2_x_s-1), (IA2_y_s-1), windowSize, windowSize);
                    Mat IA2_new_t = new Mat(image2, rectWin_b);

                    double i1_avg_new = mean(IA1_new_t).val[0];
                    double i2_avg_new = mean(IA2_new_t).val[0];

                    subtract(IA1_new_t, new Scalar(i1_avg_new), window_a_1);
                    subtract(IA2_new_t, new Scalar(i2_avg_new), window_b_1);

                    corr = openCvPIV(window_a_1, window_b_1);
                    Core.MinMaxLocResult mmr = Core.minMaxLoc(corr);

                    int c = (int) mmr.maxLoc.x;
                    int r = (int) mmr.maxLoc.y;

                    try {
                        eps_r_new[ii][jj] = (Math.log(corr.get(r-1,c)[0]) - Math.log(corr.get(r+1,c)[0])) / (2 * (Math.log(corr.get(r-1,c)[0]) -  2*Math.log(corr.get(r,c)[0]) + Math.log(corr.get(r+1,c)[0])));
                        eps_c_new[ii][jj] = (Math.log(corr.get(r,c-1)[0]) - Math.log(corr.get(r,c+1)[0])) / (2 * (Math.log(corr.get(r,c-1)[0]) - 2*Math.log(corr.get(r,c)[0]) + Math.log(corr.get(r,c+1)[0])));

                        dr_new[ii][jj] = ((windowSize-1) - (r + eps_r_new[ii][jj]))/dt;
                        dc_new[ii][jj] = ((windowSize-1) - (c + eps_c_new[ii][jj]))/dt;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    //Add new pixel displacement to pixel displacements from 1st pass
                    dr2[ii][jj] = pivCorrelation.get("v")[ii][jj] + dr_new[ii][jj];
                    dc2[ii][jj] = pivCorrelation.get("u")[ii][jj] + dc_new[ii][jj];

                    Map<String, Double> sig2NoiseRatio = sig2Noise_update(corr);
                    sig2noise[ii][jj] = sig2NoiseRatio.get("sig2Noise");
                    int x = 2;
                }
                mag[ii][jj] = Math.sqrt(Math.pow(dr2[ii][jj], 2) + Math.pow(dc2[ii][jj], 2));
            }
        }

        Map<String, double[][]> map =new HashMap();
        map.put("u",dc2);
        map.put("v",dr2);
        map.put("magnitude",mag);
        map.put("sig2Noise",sig2noise);
        return map;
    }

}
