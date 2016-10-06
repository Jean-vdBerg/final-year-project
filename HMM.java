package com.project.jean.project;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class HMM {
    private int N = 9; //Number of states, 3 per phoneme typically
    private int D = 13; //Size of the feature vector, 13 or less for MFCC
    private int G = 3; //Number of gaussians per GMM, typically 3-12
    private double[][] A; //NxN state transition probability matrix
    private double[] PI; //Nx1 initial state probability vector
    private double[][][] mu; //DxGxN mean matrix for each gaussian of each GMM
    private double[][][][] sigma; //DxDxGxN covariance matrix for each gaussian of each GMM
    private double[][] C; //NxG matrix for the weighting of the gaussians of each GMM
    private String word_name = ""; //The word that is modelled by the GMM
    private double[][] B; //B1 = NxT, gaussian distribution for all gaussians summed and scaled
    private double[][][] B_gaussians; //B2 = NxTxG, gaussian distribution for each gaussian
    private double[][] alpha; //NxT, result of forward algorithm
    private double[][] beta; //NxT, result of backward algorithm

    HMM(int states, String name, int features, int gaussians)
    {
        this.N = states;
        this.word_name = name;
        this.D = features;
        this.G = gaussians;
        this.A = new double[this.N][this.N];
        this.PI = new double[this.N];
        this.mu = new double[this.D][this.G][this.N];
        this.sigma = new double[this.D][this.D][this.G][this.N];
        this.C = new double[this.N][this.G];
    }

    public void setWord(String word)
    {
        this.word_name = word;
    }

    public String getWord()
    {
        return this.word_name;
    }

    public void initialize2(double observations[][])
    {
        PI[0] = 1;
        for (int i = 1; i < this.N; i++) {
            PI[i] = 0;
        }

        //initialize A
        //this.A = mk_stochastic(rand(this.N));

        //initialize mu
        //temp_mu = mean(observations, 2);
        //this.mu = repmat(temp_mu, [1 this.G this.N]);

        //initialize sigma
        //this.sigma = repmat(diag(diag(cov(observations'))), [1 1 this.G this.N]);

        double c_constant = 1/this.G;
        for (int m = 0; m < this.G; m++) {
            for (int i = 0; i < this.N; i++) {
                C[m][i] = c_constant;
            }
        }
    }

    public void initialize(String word, Context context)
    {
        this.word_name = word; //initialize the word name
        this.PI[0] = 1; //initialize PI
        try{
            //Scanner scanner = new Scanner(new File("assets/" + word + ".txt"));
            //Scanner scanner = new Scanner(new File(am.open(word + ".txt")));
            int res_id = context.getResources().getIdentifier(word, "raw", context.getPackageName());
            Scanner scanner = new Scanner(context.getResources().openRawResource(res_id));
            scanner.useDelimiter(",|\\n");
            scanner.useLocale(Locale.ENGLISH); //try this if having weird problem

            for (int i = 0; i < this.N; i++) {
                for (int j = 0; j < this.N; j++) {
                    A[i][j] = scanner.nextDouble();
                }
            }

            for (int i = 0; i < this.D; i++) {
                for (int j = 0; j < this.G; j++) {
                    for (int k = 0; k < this.N; k++) {
                        mu[i][j][k] = scanner.nextDouble();
                    }
                }
            }

            for (int i = 0; i < this.D; i++) {
                for (int j = 0; j < this.D; j++) {
                    for (int k = 0; k < this.G; k++) {
                        for (int l = 0; l < this.N; l++) {
                            sigma[i][j][k][l] = scanner.nextDouble();
                        }
                    }
                }
            }

            for (int i = 0; i < this.N; i++) {
                for (int j = 0; j < this.G; j++) {
                    C[i][j] = scanner.nextDouble();
                }
            }
        }
//        catch(FileNotFoundException ex)
//        {
//            Log.e("HMM", "File not found exception when initializing: " + word);
//        }
        catch(NoSuchElementException ex)
        {
            Log.e("HMM", "No such element exception when initializing: " + word);
        }
//        catch(IOException ex)
//        {
//            Log.e("HMM", "IO exception when initializing: " + word);
//        }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e("HMM", "Unknown exception: " + e);
        }
    }

    public void getGMMMatrixGaussians(double observations[][])
    {
        int T = observations.length; //obtain length of 2nd dimension, array is rectangular
        B_gaussians = new double[this.N][T][this.G];
        for (int m = 0; m < this.G; m++)
        {
            for (int i = 0; i < this.N; i++)
            {
                //calculate determinant and inverse of sigma matrix
                //assumption is that sigma is diagonal
                double determinant = 1;
                double[] sigma_inverse = new double[this.D];
                for (int j = 0; j < this.D; j++) {
                    determinant *= this.sigma[j][j][m][i];
                    sigma_inverse[j] = 1 / this.sigma[j][j][m][i];
                }
                double constant = 1/Math.sqrt(Math.pow(2*Math.PI, this.D)*determinant);

//                Log.d("Dictionary", "Constant: " + constant + " det: " + determinant);
//                Log.d("Dictionary", "Sigma: " + sigma[0][0][m][i] + ", " + sigma[1][1][m][i] + ", " + sigma[2][2][m][i] + ", " + sigma[3][3][m][i] + ", " +
//                        sigma[4][4][m][i] + ", " +sigma[5][5][m][i] + ", " + sigma[6][6][m][i] + ", " + sigma[7][7][m][i] + ", " + sigma[8][8][m][i] + ", " +
//                        sigma[9][9][m][i] + ", " + sigma[10][10][m][i] + ", " + sigma[11][11][m][i] + ", " + sigma[12][12][m][i]);

                for (int t = 0; t < T; t++)
                {
                    double matrix_constant = 0;
                    double[] temp_matrix_1 = new double[this.D];
                    double[] temp_matrix_2 = new double[this.D];

                    for (int j = 0; j < this.D; j++)
                    {
                        temp_matrix_1[j] = observations[t][j] - mu[j][m][i];
                    }
                    for (int j = 0; j < this.D; j++)
                    {
                        temp_matrix_2[j] += temp_matrix_1[j] * sigma_inverse[j];
                    }
                    for (int j = 0; j < this.D; j++)
                    {
                        matrix_constant += temp_matrix_2[j]*temp_matrix_1[j];
                    }

                    matrix_constant = Math.exp(-0.5 * matrix_constant);
                    B_gaussians[i][t][m] = constant * matrix_constant;
                    //B2(i, :, m) = mvnpdf(observations', this.mu(:, m, i)', this.sigma(:, :, m, i));
                }
            }
//            for (int i = 0; i < this.N; i++) {
//                Log.d("Dictionary", B_gaussians[i][0][0] + ", " + B_gaussians[i][1][0] + ", " + B_gaussians[i][2][0] + ", " + B_gaussians[i][3][0] + ", " +
//                        B_gaussians[i][4][0] + ", " + B_gaussians[i][5][0] + ", " + B_gaussians[i][6][0] + ", " + B_gaussians[i][7][0] + ", " + B_gaussians[i][8][0] + ", " +
//                        B_gaussians[i][9][0] + ", " + B_gaussians[i][10][0] + ", " + B_gaussians[i][11][0] + ", " + B_gaussians[i][12][0]);
//            }
        }
    }

    public void getGMMMatrixTotal(double observations[][])
    {
        int T = observations.length; //obtain length of 2nd dimension, array is rectangular
        getGMMMatrixGaussians(observations);
        B = new double[this.N][T];
        for (int i = 0; i < this.N; i++)
        {
            for (int m = 0; m < this.G; m++)
            {
                for (int t = 0; t < T; t++)
                {
                    B[i][t] += this.C[i][m]*B_gaussians[i][t][m];
                    //B1(i, :) = B1(i, :) + this.C(i, m)*B2(i, :, m);
                }
            }
        }
//        for (int i = 0; i < this.N; i++) {
//            Log.d("Dictionary", B[i][0] + ", " + B[i][1] + ", " + B[i][2] + ", " + B[i][3] + ", " +
//                    B[i][4] + ", " + B[i][5] + ", " + B[i][6] + ", " + B[i][7] + ", " + B[i][8] + ", " +
//                    B[i][9] + ", " + B[i][10] + ", " + B[i][11] + ", " + B[i][12]);
//        }
        //// TODO: 2016/10/05 correct the b matrix generation 
    }

    public void getGMMMatrixTotal_2(double observations[][])
    {
        int T = observations.length; //obtain length of 2nd dimension, array is rectangular
        B = new double[this.N][T];
        for (int i = 0; i < this.N; i++)
        {
            for (int t = 0; t < T; t++)
            {
                B[i][t] = 0;
                //B2(i, :, m) = mvnpdf(observations', this.mu(:, m, i)', this.sigma(:, :, m, i));
            }
        }
    }

    public double forward()
    {
        double log_likelihood = 0;
        int T = B[0].length;
        alpha = new double[this.N][T];

        for (int t = 0; t < T; t++)
        {
            if(t == 0)
            {
                //initialization
                for (int i = 0; i < this.N; i++)
                {
                    alpha[i][t] = B[i][t] * PI[i];
                    //alpha(:, 1) = B(:, 1) .* this.PI; %(equation 2)
                }
            }
            else
            {
                //recursive formula
                for (int i = 0; i < this.N; i++)
                {
                    alpha[i][t] = 0;
                    for (int j = 0; j < this.N; j++) {
                        alpha[i][t] += alpha[j][t - 1] * A[j][i];
                    }
                    alpha[i][t] *= B[i][t];
                    //alpha(:, t) = B(:, t) .* (this.A' * alpha(:, t - 1)); %(equation 3)
                }
            }

            //apply scaling
            double c_t = 0;
            for (int i = 0; i < this.N; i++)
            {
                c_t += alpha[i][t];
            }
            for (int i = 0; i < this.N; i++)
            {
                alpha[i][t] = alpha[i][t]/c_t;
                //alpha(:, t) = alpha(:, t) ./ c_t; %(equation 24)
            }

            //probability calculation
            log_likelihood += Math.log(c_t);
            //log_likelihood = log_likelihood + log(c_t); %(equation 27)
        }
        return log_likelihood;
    }

    public void backward()
    {
        int T = B[0].length;
        beta = new double[this.N][T];

        //initialization
        for (int i = 0; i < this.N; i++) {
            beta[i][T - 1] = 1;
        }
        for (int t = T - 2; t >= 0; t--)
        {
            //recursive formula
            for (int i = 0; i < this.N; i++)
            {
                beta[i][t] = 0;
                for (int j = 0; j < this.N; j++) {
                    beta[i][t] += A[i][j] * B[j][t + 1] * beta[j][t + 1];
                }
                //alpha(:, t) = B(:, t) .* (this.A' * alpha(:, t - 1)); %(equation 3)
            }

            //apply scaling
            double c_t = 0;
            for (int i = 0; i < this.N; i++)
            {
                c_t += beta[i][t];
            }
            for (int i = 0; i < this.N; i++)
            {
                beta[i][t] = beta[i][t]/c_t;
                //alpha(:, t) = alpha(:, t) ./ c_t; %(equation 24)
            }
        }
    }

    public double stateProbability(double observations[][])
    {
        getGMMMatrixTotal(observations);
        return forward();
    }

    public double train(double observations[][])
    {
        int T = observations.length;
        getGMMMatrixTotal(observations);
        double log_likelihood = forward();
        backward();

        double[][] gamma = new double[this.N][T];
        double[][][] delta = new double[this.N][this.N][T];
        double[][][] epsilon = new double[this.N][this.G][T];

        //do variable estimation for gamma delta and epsilon

        //do matrix training

        return log_likelihood;
    }
}
