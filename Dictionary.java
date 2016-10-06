package com.project.jean.project;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import org.jtransforms.fft.DoubleFFT_1D;
/**
 * Created by Jean on 2016/10/03.
 */
public class Dictionary {
    private HMM[] word_list;
    private int training_epochs = 10;
    private int dictionary_size = 0;
    private int mfcc_features = 13;
    private int hmm_states = 3;
    private int gaussians_per_state = 1;
    private Context mCtx;

    Dictionary(Context context)
    {
        mCtx = context;
        word_list = new HMM[100];
        for (int i = 0; i < word_list.length; i++) {
            word_list[i] = new HMM(hmm_states, "", mfcc_features, gaussians_per_state);
        }
        //declares a word_list of 100 HMMs, 1 HMM per word

        //AssetManager am = mCtx.getAssets();

        word_list[0].initialize("apple", mCtx);

        word_list[1].initialize("banana", mCtx);

        word_list[2].initialize("hello", mCtx);

        word_list[3].initialize("kiwi", mCtx);

        word_list[4].initialize("lime", mCtx);

        word_list[5].initialize("orange", mCtx);

        word_list[6].initialize("peach", mCtx);

        word_list[7].initialize("pineapple", mCtx);

        dictionary_size += 8;
    }

    public void addWord(String word_name, double[][] audio_signal, int signal_length)
    {
        int amnt_signals = audio_signal.length; //requires audio signal to be amount of signals by signal data
        double[][] all_observations;
        double[][] current_observations;
        all_observations = MFCC(audio_signal[0], signal_length); //initialize all_observations to first audio signal
        for (int i = 1; i < amnt_signals; i++)
        {
            current_observations = MFCC(audio_signal[i], signal_length);
            all_observations = concat(all_observations, current_observations); //add current observation
        }

        HMM new_word = new HMM(hmm_states, word_name, mfcc_features, gaussians_per_state);
        new_word.initialize2(all_observations);
        for (int i = 0; i < training_epochs; i++) {
            new_word.train(all_observations);
        }
        word_list[dictionary_size] = new_word;
        dictionary_size++;
    }

    public double[][] concat(double[][] a, double[][] b) { //concat two rectangular arrays with same x dimension
        int length_a = a[0].length;
        int length_b = b[0].length;
        double[][] c = new double[a.length][length_a + length_b];
        System.arraycopy(a, 0, c, 0, length_a);
        System.arraycopy(b, 0, c, length_a, length_b);
        return c;
    }

    public String recognize(double[] audio_signal, int signal_length)
    {
        String most_likely_word = "";
        double[][] observations = MFCC(audio_signal, signal_length);
        double max_likelihood = -1000000;
        double current_likelihood = 0;

        for (int i = 0; i < dictionary_size; i++)
        {
            current_likelihood = word_list[i].stateProbability(observations);
            Log.d("Dictionary", "Likelihood of word being " + word_list[i].getWord() + " is " + current_likelihood);
            if(current_likelihood > max_likelihood)
            {
                max_likelihood = current_likelihood;
                most_likely_word = word_list[i].getWord();
            }
        }

        return most_likely_word;
    }

    private double[][] MFCC (double[] audio_signal, int signal_length)
    {
        boolean ETSI_standard = true;
        boolean input_scaling = false;
        boolean include_frame_energy = false;
        boolean output_observations = false;

//        for (int i = 1000; i < 1050; i++) {
//            Log.d("Dictionary", "Sample " + i + " = " + audio_signal[i]);
//        }

        int fs = 8000;
        double pre_emph = 0.97;
        int samples_per_frame = 200;
        int frame_step = 80;
        int num_samples = signal_length;
        int num_samples_padded = num_samples + (samples_per_frame - num_samples%samples_per_frame);
        int fft_size = 256;
        int fft_len_unique = fft_size/2 + 1;
        int filterbank_size = 23;
        int filterbank_coeffs_amnt = filterbank_size + 2;
        int f_filterbank_l = 64;
        int f_filterbank_h = 4000;
        int f_min = 0;
        int f_max = fs / 2;
        int cepstra_coeffs_amnt = mfcc_features;
        int cepstra_lifter = 22;

        if(input_scaling)
        {
            for (int i = 0; i < num_samples; i++)
            {
                audio_signal[i] = 32768 * audio_signal[i];
            }
        }

        double[] i_t = new double[num_samples_padded];

        System.arraycopy(audio_signal, 0, i_t, 0, num_samples);
        double[] i_t_emp = new double[samples_per_frame];
        double[] t_wind = new double[fft_size]; //was of size samples_per_frame in matlab
        //this is done to make it have zero padding
        double[] window = new double[samples_per_frame];
        double[] f_wind = new double[fft_size];
        double[] f_wind_abs = new double[fft_size];

        //window estimation
        for (int i = 0; i < samples_per_frame; i++) {
            window[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (samples_per_frame - 1));
        }

        //f = linspace(f_min, f_max, fft_len_unique);
        double[] f = new double[fft_len_unique];
        for (int i = 0; i < fft_len_unique; i++) {
            double temp = i*(f_max - f_min);
            f[i] = f_min + temp/(fft_len_unique - 1);
        }
        double mel_filterbank_l = FToMel(f_filterbank_l);
        double mel_filterbank_h = FToMel(f_filterbank_h);
        double mel_filterbank_diff = mel_filterbank_h - mel_filterbank_l;
        double filterbank_spacing = mel_filterbank_diff/(filterbank_coeffs_amnt - 1);
        double mel_filterbank[] = new double[filterbank_coeffs_amnt];
        for (int i = 0; i < filterbank_coeffs_amnt; i++) {
            mel_filterbank[i] = mel_filterbank_l + i*filterbank_spacing;
        }
        double f_filterbank[] = new double[filterbank_coeffs_amnt];
        for (int i = 0; i < filterbank_coeffs_amnt; i++) {
            f_filterbank[i] = MelToF(mel_filterbank[i]);
        }
        double[][] h = new double[filterbank_size][fft_len_unique];
        double[] coeffs = new double[filterbank_size];
        double[] coeffs_log = new double[filterbank_size];
        double[] coeffs_dct = new double[filterbank_size];

        int frames_amnt = (int)Math.floor((num_samples - (num_samples%samples_per_frame))/frame_step) + 1;
        int current_frame = 0;
        double[][] mfcc = new double[frames_amnt][cepstra_coeffs_amnt];
        DoubleFFT_1D fft = new DoubleFFT_1D(fft_size);

        //dct matrix estimation
        double[][] dct = new double[cepstra_coeffs_amnt][filterbank_size];
        if(!ETSI_standard) {
            for (int i = 0; i < cepstra_coeffs_amnt; i++) {
                for (int j = 0; j < filterbank_size; j++) {
                    dct[i][j] = Math.sqrt(2/filterbank_size)*Math.cos(Math.PI * i * (j + 0.5)/filterbank_size);
                }
            }
        }
        else
        {
            for (int i = 0; i < cepstra_coeffs_amnt; i++) {
                for (int j = 0; j < filterbank_size; j++) {
                    dct[i][j] = Math.cos(Math.PI * i * (j + 0.5)/filterbank_size);
                }
            }
        }

        //lifter estimation
        double[][] lifter = new double[cepstra_coeffs_amnt][cepstra_coeffs_amnt];
        for (int i = 0; i < cepstra_coeffs_amnt; i++) {
            for (int j = 0; j < cepstra_coeffs_amnt; j++) {
                if(i == j)
                    lifter[i][j] = 1 + cepstra_lifter*0.5*Math.sin(Math.PI * i / cepstra_lifter);
                else
                    lifter[i][j] = 0;
            }
        }

        //filterbank estimation
        for (int i = 0; i < filterbank_size; i++)
        {
            for (int j = 0; j < fft_len_unique; j++)
            {
                if(f[j] >= f_filterbank[i] && f[j] <= f_filterbank[i + 1])
                    h[i][j] = (f[j] - f_filterbank[i])/(f_filterbank[i + 1] - f_filterbank[i]);
                else if(f[j] >= f_filterbank[i + 1] && f[j] <= f_filterbank[i + 2])
                    h[i][j] = (f_filterbank[i + 2] - f[j])/(f_filterbank[i + 2] - f_filterbank[i + 1]);
                else
                    h[i][j] = 0;
            }
        }

        for (int i = 0; i < (num_samples - num_samples%samples_per_frame - 1); i = i + frame_step)
        {
            //compute frame energy
            double frame_energy = 0;
            for (int j = 0; j < samples_per_frame; j++)
            {
                frame_energy += Math.pow(i_t[i + j], 2);
            }
            if(frame_energy < 2*Math.pow(10, -22))
                frame_energy = -50;
            else
                frame_energy = Math.log(frame_energy);
            //frame energy output identical for all t

            //apply pre-emphasis
            for (int j = 0; j < samples_per_frame; j++)
            {
                if(i == 0 && j == 0)
                    i_t_emp[j] = i_t[0];
                else
                    i_t_emp[j] = i_t[i + j] - pre_emph*i_t[i + j - 1];
            }
            //i_t_emp output identical for all t

            //apply window
            for (int j = 0; j < samples_per_frame; j++)
            {
                t_wind[j] = i_t_emp[j]*window[j];
            }
            for (int j = samples_per_frame; j < fft_size; j++) {
                t_wind[j] = 0;
            }

                    //apply fft (http://wendykierp.github.io/JTransforms/apidocs/)
            fft.realForward(t_wind);
            //if this doesnt work try https://www.ee.columbia.edu/~ronw/code/MEAPsoft/doc/html/FFT_8java-source.html

            //find magnitude of the real and complex values
            f_wind[0] = Math.abs(t_wind[0]);
            f_wind[fft_size/2] = Math.abs(t_wind[1]);
            for (int j = 1; j < fft_size/2; j++) {
                f_wind[j] = Math.sqrt(Math.pow(t_wind[2*j], 2) + Math.pow(t_wind[2*j + 1], 2));
            }

            //recreate the symmetry of the fft
//            System.arraycopy(f_wind, 0, f_wind_abs, 0, fft_size/2 + 1);
//            int index = fft_size/2 - 1;
//            for (int j = fft_size/2 + 1; j < fft_size; j++) {
//                f_wind_abs[j] = f_wind[index];
//                index--;
//            }

            //apply filterbank
            for (int j = 0; j < filterbank_size; j++)
            {
                coeffs[j] = 0;
                for (int k = 0; k < fft_len_unique; k++)
                {
                    coeffs[j] += h[j][k]*f_wind[k]; //problem is here with one of these matrices or the multiplication
                }
            }

            //convert to log domain
            for (int j = 0; j < filterbank_size; j++)
            {
                coeffs_log[j] = Math.log(coeffs[j]);
            }

            //apply dct
            for (int j = 0; j < cepstra_coeffs_amnt; j++) {
                coeffs_dct[j] = 0;
                for (int k = 0; k < filterbank_size; k++) {
                    coeffs_dct[j] += dct[j][k] * coeffs_log[k];
                }
            }

            //apply liftering if needed
            if(!ETSI_standard)
            {
                for (int j = 0; j < cepstra_coeffs_amnt; j++)
                {
                    mfcc[current_frame][j] = 0;
                    for (int k = 0; k < cepstra_coeffs_amnt; k++)
                    {
                        mfcc[current_frame][j] += lifter[j][k] * coeffs_dct[j];
                    }
                }
            }
            else
            {
                System.arraycopy(coeffs_dct, 0, mfcc[current_frame], 0, cepstra_coeffs_amnt);
//                for (int j = 0; j < cepstra_coeffs_amnt; j++)
//                {
//                    mfcc[current_frame][j] = coeffs_dct[j];
//                }
            }

            if(mfcc[current_frame][0] < -50)
                mfcc[current_frame][0] = -50;

            //include frame energy if needed
            if(include_frame_energy)
                mfcc[current_frame][cepstra_coeffs_amnt + 1] = frame_energy;

            current_frame++;
        }

        if(output_observations) {
            for (int i = 0; i < frames_amnt; i++) {
                Log.d("Dictionary", mfcc[i][0] + ", " + mfcc[i][1] + ", " + mfcc[i][2] + ", " + mfcc[i][3] + ", " +
                        mfcc[i][4] + ", " + mfcc[i][5] + ", " + mfcc[i][6] + ", " + mfcc[i][7] + ", " + mfcc[i][8] + ", " +
                        mfcc[i][9] + ", " + mfcc[i][10] + ", " + mfcc[i][11] + ", " + mfcc[i][12]);
            }
        }

        return mfcc;
    }

    private double FToMel(double hz)
    {
        return 2595 * Math.log10(1 + hz/700);
    }

    private double MelToF(double mel)
    {
        return 700 * Math.pow(10, (mel/2595)) - 700;
    }

}
