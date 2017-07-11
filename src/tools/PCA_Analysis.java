package tools;

/*
*   Application:   PCA_Analysis
*
*   USAGE:  An application performing a basic Principal Component Analysis.
*           This application illustrates the class, PCA.
*
*   WRITTEN BY: Dr Michael Thomas Flanagan
*
*   DATE:       December 2010
*
*   DOCUMENTATION:
*   See Michael Thomas Flanagan's Java library on-line web pages:
*   http://www.ee.ucl.ac.uk/~mflanaga/java/PCA_Analysis.html
*   http://www.ee.ucl.ac.uk/~mflanaga/java/PCA.html
*
*   Copyright (c) 2010 - 2014
*
*   PERMISSION TO COPY:
*   Permission to use, copy and modify this software and its documentation for
*   NON-COMMERCIAL purposes is granted, without fee, provided that an acknowledgement
*   to the author, Dr Michael Thomas Flanagan at www.ee.ucl.ac.uk/~mflanaga,
*   appears in all copies and associated documentation or publications.
*
*   Public listing of the source codes on the internet is not permitted.
*
*   Redistribution of the source codes or of the flanagan.jar file is not permitted.
*
*   Redistribution in binary form of all or parts of these classes is not permitted.
*
*   Dr Michael Thomas Flanagan makes no representations about the suitability
*   or fitness of the software for any or for a particular purpose.
*   Dr Michael Thomas Flanagan shall not be liable for any damages suffered
*   as a result of using, modifying or distributing this software or its derivatives.
*
***************************************************************************************/

import flanagan.math.Fmath;
import flanagan.io.Db;
import flanagan.analysis.PCA;

import javax.swing.JOptionPane;

public class PCA_Analysis{

    public static void main(String[] arg){

        // Create an instance of PCA
        PCA pca = new PCA();
        pca.letterToNumeral();
        pca.useGaussianDeviates();
        pca.numberOfDecimalPlacesAll(6);

        // Introductory message
        String message = "This program performs a basic Principal Component Analysis with a Varimax Rotation.\n";
        message += "It accepts the scores from a text file and requests information about the scores through \n";
        message += "a series of dialogue boxes.\n\n";
        message += "Please select the scores data file from the file select window and respond to each dialogue box\n";
        message += "that appears on closing this message (by clicking on the OK button)";
        JOptionPane.showMessageDialog(null, message, "Program PCA_Analysis: Introduction", JOptionPane.INFORMATION_MESSAGE);

        // Select order of scores input
        String headerComment = "Is the scores data file organized as";
        String[] comments = {"scores on each row corresponding to an individual\n", "scores on each row corresponding to an item\n"};
        String[] boxTitles = {"row per individual", "row per item"};
        int defaultBox = 1;
        int readOption =  Db.optionBox(headerComment, comments, boxTitles, defaultBox);

        // Set missing response replacement
        headerComment = "Which Missing Response Replacement Method do you require?";
        String[] comments3 = {"the missing response is replaced by zero\n", "the missing response is replaced by that person's mean\n", "the missing response is replaced by that item's mean    (default option)\n", "the missing response is replaced by the overall mean\n", "the missing response is replaced by a user supplied score for each missing response. A value will be requested, \n          via a dialog box, each time a missing response is encounterd as the data is processed\n"};
        String[] boxTitles3 = {"zero", "person's mean", "item's mean", "overall mean", "user supplied value"};
        defaultBox = 3;
        int replacementOption =  Db.optionBox(headerComment, comments3, boxTitles3, defaultBox);
        pca.setMissingDataOption(replacementOption);

        // Set person deletion percentage
        Db.setTypeInfoOption(2);
        message = "Program PCA_Analysis [See documentation: Box Four]:\n\nEnter the person deletion percentage (pdpc)\n";
        message += "    Example pdpc values:\n";
        message += "    pdpc = 0:      a person is deleted after missing one response\n";
        message += "    pdpc = 50:     a person is deleted after missing more than 50% of the response\n";
        message += "    pdpc = 100:    a person is only deleted if they miss all responses\n";
        double pdpc = Db.readDouble(message, 100.0);
        if(pdpc<0.0){
            message = "Person deletion percentage, " + pdpc + ", must be greater than or equal to zero.\nIt has been reset to zero";
            JOptionPane.showMessageDialog(null, message, "Program PCA_Analysis: Warning", JOptionPane.WARNING_MESSAGE);
            pdpc = 0;
        }
        if(pdpc>100){
            message = "Person deletion percentage, " + pdpc + ", must be less than or equal to 100.0\nIt has been reset to 100.0";
            JOptionPane.showMessageDialog(null, message, "Program PCA_Analysis: Warning", JOptionPane.WARNING_MESSAGE);
            pdpc = 100.0;
        }
        pca.setPersonDeletionPercentage(pdpc);

        // Set item deletion percentage
        message = "Program PCA_Analysis [See documentation: Box Five]:\n\nEnter the item deletion percentage (idpc)\n";
        message += "    Example idpc values:\n";
        message += "    idpc = 0:      an item is deleted after a single response to that item has been missed\n";
        message += "    idpc = 50:     an item is deleted after more than 50% of the responses to that item are missed\n";
        message += "    idpc = 100:    an item is only deleted if no person responds to that item\n";
        double idpc = Db.readDouble(message, 100.0);
        if(idpc<0.0){
            message = "Item deletion percentage, " + idpc + ", must be greater than or equal to zero.\nIt has been reset to zero";
            JOptionPane.showMessageDialog(null, message, "Program PCA_Analysis: Warning", JOptionPane.WARNING_MESSAGE);
            idpc = 0;
        }
        if(idpc>100){
            message = "Item deletion percentage, " + idpc + ", must be less than or equal to 100.0\nIt has been reset to 100.0";
            JOptionPane.showMessageDialog(null, message, "Program PCA_Analysis: Warning", JOptionPane.WARNING_MESSAGE);
            idpc = 100.0;
        }
        pca.setItemDeletionPercentage(pdpc);

        if(readOption==1){
            pca.readScoresAsRowPerPerson();
        }
        else{
            pca.readScoresAsRowPerItem();
        }


        // Select type of output file
        headerComment = "Which Analysis Output File type do you require?";
        String[] comments2 = {"Output as a text file (.txt)\n", "Output as an Excel readable file (.xls)                        .\n"};
        String[] boxTitles2 = {"text File (.txt)", "Excel File (.xls)"};
        defaultBox = 1;
        int fileOption =  Db.optionBox(headerComment, comments2, boxTitles2, defaultBox);
        pca.setOutputFileType(fileOption);
        String extn = ".txt";
        if(fileOption==2)extn = ".xls";

        // Create output file name
        String inputName = pca.getInputFileName();
        String outputName = null;
        String outputNameD = null;
        int pos = inputName.lastIndexOf('.');
        if(pos==-1){
            outputNameD = inputName+"Analysis"+extn;
        }
        else{
            outputNameD = inputName.substring(0,pos)+ "Analysis" + extn;
        }

        boolean checkExt = false;
        message = "Program PCA_Analysis:\n\nEnter the Analysis Output File name\n\n";
        message += "After entering this name the program may take several seconds before displaying the next graph and dialogue box\n\n";

        outputName = Db.readLine(message, outputNameD);
        if(!outputName.equals(outputNameD)){
            String outputNameHold = outputName;
            pos = outputName.lastIndexOf('.');
            if(pos==-1){
                outputName += extn;
                checkExt = true;
            }
            else{
                if(fileOption==1){
                    if(!(outputName.substring(pos)).equals(".txt")){
                        outputName = outputName.substring(0,pos) + extn;
                        checkExt = true;
                    }
                }
                else{
                    if(!(outputName.substring(pos)).equals(".xls")){
                        outputName = outputName.substring(0,pos) + extn;
                        checkExt = true;
                    }
                }
            }
            if(checkExt){
                if(fileOption==1){
                    message = "You chose a text file (.txt) as the output file type\n";
                }
                else{
                    message = "You chose an Excel readable file (.xls) as the output file type\n";
                }
                message += "consequently your file name, '" + outputNameHold + "', has been\n";
                message += "changed to '" + outputName + "'\n";
                JOptionPane.showMessageDialog(null, message, "Program PCA_Analysis: Output File", JOptionPane.WARNING_MESSAGE);
            }
        }

        // Perform models.similarity_models.analysis
        pca.analysis(outputName);

        message = "Input File Name = '" + inputName + "'\n\n";
        message += "The models.similarity_models.analysis has been written to the output file '" + outputName + "'\n\n";
        message += "Do you wish to close the scree plot?\n\n";
        message += "If you answer NO the program must then be terminated later by clicking on the\nclose icon (white cross on red background in the top right hand corner) on any of the plots\n";

        boolean answer = Db.yesNo(message);
        if(answer){
            System.exit(0);
        }

    }

}