package dbuhler.stitcher;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.features2d.KeyPoint;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * This class uses OpenCV functions for stitching two images.
 *
 * @author  Dan Buhler
 * @version 2015-04-06
 */
public final class Stitcher
{
    public static final int NUM_STEPS = 5;

    private static final int    DETECTOR_TYPE    = FeatureDetector.GFTT;
    private static final int    EXTRACTOR_TYPE   = DescriptorExtractor.FREAK;
    private static final int    MATCHER_TYPE     = DescriptorMatcher.BRUTEFORCE;
    private static final int    MAX_DIMENSION    = 1024;
    private static final double MATCH_THRESHOLD  = 3.0;
    private static final double RANSAC_THRESHOLD = 1.0;
    private static final Scalar COLOR_MATCH      = new Scalar(255, 0, 0, 255);

    private Bitmap   bitmapL;
    private Bitmap   bitmapR;
    private Bitmap[] steps;

    private FeatureDetector     featureDetector;
    private DescriptorExtractor descriptorExtractor;
    private DescriptorMatcher   descriptorMatcher;

    /**
     * Initializes and runs the image stitcher for the two given bitmaps.
     *
     * @param bitmapL The left image to stitch.
     * @param bitmapR The right image to stitch.
     */
    public Stitcher(Bitmap bitmapL, Bitmap bitmapR)
    {
        this.bitmapL = bitmapL;
        this.bitmapR = bitmapR;
        steps = new Bitmap[NUM_STEPS];
        featureDetector = FeatureDetector.create(DETECTOR_TYPE);
        descriptorExtractor = DescriptorExtractor.create(EXTRACTOR_TYPE);
        descriptorMatcher = DescriptorMatcher.create(MATCHER_TYPE);

        run();
    }

    /**
     * Returns the image from the i-th step of the stitching process as a bitmap. The final image
     * is retrieved for i = NUM_STEPS - 1.
     *
     * @param i The step number between 0 and NUM_STEPS - 1.
     * @return The image from the i-th step.
     */
    public Bitmap getStep(int i)
    {
        return steps[i];
    }

    /**
     * Performs the stitching and creates NUM_STEPS bitmaps showing the intermediate steps as well
     * as the end result.
     */
    private void run()
    {
        // Create colour and greyscale image matrices.
        Mat colorImageL = createMatrix(bitmapL);
        Mat colorImageR = createMatrix(bitmapR);
        Mat grayImageL  = new Mat();
        Mat grayImageR  = new Mat();
        Imgproc.cvtColor(colorImageL, grayImageL, Imgproc.COLOR_RGB2GRAY);
        Imgproc.cvtColor(colorImageR, grayImageR, Imgproc.COLOR_RGB2GRAY);

        // Image for Step 1: Original Images.
        steps[0] = createBitmap(mergeMatrices(colorImageL, colorImageR));

        // Detect features and extract the feature descriptors.
        Mat           descriptorsL = new Mat();
        Mat           descriptorsR = new Mat();
        MatOfKeyPoint keyPointsL   = detectFeatures(grayImageL, descriptorsL, 0.5, 1.0);
        MatOfKeyPoint keyPointsR   = detectFeatures(grayImageR, descriptorsR, 0.0, 0.5);

        // Image for Step 2: Feature Detection.
        steps[1] = drawFeatures(grayImageL, grayImageR, keyPointsL, keyPointsR);

        // Find matches between the detected features.
        MatOfDMatch matches = matchFeatures(descriptorsL, descriptorsR);

        // Image for Step 3: Feature Matching.
        steps[2] = drawMatches(grayImageL, grayImageR, keyPointsL, keyPointsR, matches);

        // Find homography and the matches used for it.
        MatOfByte   matchMask   = new MatOfByte();
        Mat         homography  = findHomography(keyPointsL, keyPointsR, matches, matchMask);
        MatOfDMatch usedMatches = filterMatches(matches, matchMask);

        // Image for Step 4: Matches for Homography.
        steps[3] = drawMatches(grayImageL, grayImageR, keyPointsL, keyPointsR, usedMatches);

        // Image for Step 5: Stitched Images.
        steps[4] = mergeImages(colorImageL, colorImageR, homography);
    }

    /**
     * Creates and returns a bitmap from a matrix.
     *
     * @param matrix The matrix defining the bitmap.
     * @return The image created from the matrix.
     */
    private Bitmap createBitmap(Mat matrix)
    {
        Bitmap bitmap = Bitmap.createBitmap(matrix.cols(), matrix.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(matrix, bitmap);
        return bitmap;
    }

    /**
     * Creates and returns a matrix from a bitmap. If the bitmap has a width or height greater than
     * MAX_DIMENSION, the matrix will be resized accordingly.
     *
     * @param bitmap The bitmap to get the matrix from.
     * @return The matrix defining the bitmap.
     */
    private Mat createMatrix(Bitmap bitmap)
    {
        Mat matrix = new Mat();
        Utils.bitmapToMat(bitmap, matrix);

        // Determine scale factor for too large images.
        double scale = 1.0 * MAX_DIMENSION / Math.max(matrix.rows(), matrix.cols());

        if (scale < 1.0)
        {
            Imgproc.resize(matrix, matrix, new Size(), scale, scale, Imgproc.INTER_LINEAR);
        }

        return matrix;
    }

    /**
     * Merges two matrices side-by-side into a single matrix.
     *
     * @param matrixL The left matrix to merge.
     * @param matrixR The right matrix to merge.
     * @return The merged matrix.
     */
    private Mat mergeMatrices(Mat matrixL, Mat matrixR)
    {
        Mat mergedMatrix = new Mat(
                Math.max(matrixL.rows(), matrixR.rows()),
                matrixL.cols() + matrixR.cols(), matrixL.type());

        mergedMatrix.setTo(Scalar.all(0));

        // Define the regions where each matrix is copied to.
        Rect subRectL = new Rect(0,              0, matrixL.cols(), matrixL.rows());
        Rect subRectR = new Rect(matrixL.cols(), 0, matrixR.cols(), matrixR.rows());

        matrixL.copyTo(new Mat(mergedMatrix, subRectL));
        matrixR.copyTo(new Mat(mergedMatrix, subRectR));

        return mergedMatrix;
    }

    /**
     * Creates and returns a mask for a matrix such that the values are 1 for all columns i where
     * min * n <= i < max * n and 0 otherwise, where n is the number of columns in the matrix.
     *
     * @param matrix The matrix to create the mask for.
     * @param min    The lower bound as a fraction of the column number.
     * @param max    The upper bound as a fraction of the column number.
     * @return The created mask for the matrix.
     */
    private Mat createMask(Mat matrix, double min, double max)
    {
        Mat mask = new Mat(matrix.size(), CvType.CV_8UC1);
        int xMin = (int)(min * matrix.cols());
        int xMax = (int)(max * matrix.cols());

        List<MatOfPoint> points = new ArrayList<>();

        points.add(new MatOfPoint(
                new Point(xMin,     0),
                new Point(xMin,     matrix.rows() - 1),
                new Point(xMax - 1, matrix.rows() - 1),
                new Point(xMax - 1, 0)));

        mask.setTo(Scalar.all(0));

        Core.fillPoly(mask, points, new Scalar(255));

        return mask;
    }

    /**
     * Detects features in the given image and stores them in the given descriptors matrix. The area
     * for feature detection is restricted to columns i where min * n <= i < max * n, where n is the
     * number of columns in the image matrix. Returns the feature key points as a matrix.
     *
     * @param image       The image to detect features in.
     * @param min         The lower bound of the detection area as a fraction of the column number.
     * @param max         The upper bound of the detection area as a fraction of the column number.
     * @param descriptors The matrix that will contain the feature descriptors.
     * @return The key points of the detected features.
     */
    private MatOfKeyPoint detectFeatures(Mat image, Mat descriptors, double min, double max)
    {
        Mat           mask      = createMask(image, min, max);
        MatOfKeyPoint keyPoints = new MatOfKeyPoint();

        featureDetector.detect(image, keyPoints, mask);
        descriptorExtractor.compute(image, keyPoints, descriptors);

        return keyPoints;
    }

    /**
     * Draws the given key points onto the given images and returns the resulting images as one
     * combined bitmap.
     *
     * @param imageL     The left image to draw key points onto.
     * @param imageR     The right image to draw key points onto.
     * @param keyPointsL The key points for the left image.
     * @param keyPointsR The key points for the right image.
     * @return The bitmap showing both images with their key points.
     */
    private Bitmap drawFeatures(Mat imageL, Mat imageR,
                                MatOfKeyPoint keyPointsL, MatOfKeyPoint keyPointsR)
    {
        Mat newImageL = new Mat();
        Mat newImageR = new Mat();
        Features2d.drawKeypoints(imageL, keyPointsL, newImageL, COLOR_MATCH, 0);
        Features2d.drawKeypoints(imageR, keyPointsR, newImageR, COLOR_MATCH, 0);
        return createBitmap(mergeMatrices(newImageL, newImageR));
    }

    /**
     * Find and returns the matches in the features given by the two descriptor matrices.
     *
     * @param descriptorsL The feature descriptors for the left image.
     * @param descriptorsR The feature descriptors for the right image.
     * @return The matrix of good feature matches.
     */
    private MatOfDMatch matchFeatures(Mat descriptorsL, Mat descriptorsR)
    {
        MatOfDMatch matches = new MatOfDMatch();

        descriptorMatcher.match(descriptorsL, descriptorsR, matches);

        DMatch[] matchesArray = matches.toArray();

        // Find the distance for the best match.
        double minDistance = Double.MAX_VALUE;

        for (DMatch match : matchesArray)
        {
            if (match.distance < minDistance)
            {
                minDistance = match.distance;
            }
        }

        // Keep only matches that at most MATCH_THRESHOLD times worse than the best match.
        List<DMatch> goodMatches = new ArrayList<>();

        for (DMatch match : matchesArray)
        {
            if (match.distance < MATCH_THRESHOLD * minDistance)
            {
                goodMatches.add(match);
            }
        }

        matches.fromList(goodMatches);
        return matches;
    }

    /**
     * Draws the given matches of the given key points as lines onto the combined given images and
     * returns the result as a bitmap.
     *
     * @param imageL     The left image to draw matches onto.
     * @param imageR     The right image to draw matches onto.
     * @param keyPointsL The key points for the left image.
     * @param keyPointsR The key points for the right image.
     * @param matches    The matches between the key points.
     * @return The bitmap showing both images with their matching key points.
     */
    private Bitmap drawMatches(Mat imageL, Mat imageR, MatOfKeyPoint keyPointsL,
                               MatOfKeyPoint keyPointsR, MatOfDMatch matches)
    {
        Mat newImage = new Mat();
        Features2d.drawMatches(imageL, keyPointsL, imageR, keyPointsR, matches, newImage,
                               COLOR_MATCH, COLOR_MATCH, new MatOfByte(),
                               Features2d.NOT_DRAW_SINGLE_POINTS);

        return createBitmap(newImage);
    }

    /**
     * Finds and returns the homography based on the given matches between the given key points
     * using the RANSAC algorithm. Stores the mask of used matches in the given mask matrix.
     *
     * @param keyPointsL The key points for the left image.
     * @param keyPointsR The key points for the right image.
     * @param matches    The matches between the key points.
     * @param mask       The matrix that will contain the mask of used matches.
     * @return The homography between the two images based on their matched key points.
     */
    private Mat findHomography(MatOfKeyPoint keyPointsL, MatOfKeyPoint keyPointsR,
                               MatOfDMatch matches, MatOfByte mask)
    {
        KeyPoint[]   keyPointsArrayL     = keyPointsL.toArray();
        KeyPoint[]   keyPointsArrayR     = keyPointsR.toArray();
        DMatch[]     matchesArray        = matches.toArray();
        Point[]      matchedPointsArrayL = new Point[matchesArray.length];
        Point[]      matchedPointsArrayR = new Point[matchesArray.length];
        MatOfPoint2f matchedPointsL      = new MatOfPoint2f();
        MatOfPoint2f matchedPointsR      = new MatOfPoint2f();

        for (int i = 0; i < matchesArray.length; ++i)
        {
            matchedPointsArrayL[i] = keyPointsArrayL[matchesArray[i].queryIdx].pt;
            matchedPointsArrayR[i] = keyPointsArrayR[matchesArray[i].trainIdx].pt;
        }

        matchedPointsL.fromArray(matchedPointsArrayL);
        matchedPointsR.fromArray(matchedPointsArrayR);

        return Calib3d.findHomography(matchedPointsR, matchedPointsL,
                                      Calib3d.RANSAC, RANSAC_THRESHOLD, mask);
    }

    /**
     * Filters the given matches by the given mask and returns the matrix of the filtered matches.
     *
     * @param matches The matches to filter.
     * @param mask    The filter mask for the matches.
     * @return The filtered matches.
     */
    private MatOfDMatch filterMatches(MatOfDMatch matches, MatOfByte mask)
    {
        byte[]       maskArray    = mask.toArray();
        DMatch[]     matchesArray = matches.toArray();
        List<DMatch> matchesList  = new ArrayList<>();

        for (int i = 0; i < maskArray.length; ++i)
        {
            if (maskArray[i] == 1)
            {
                matchesList.add(matchesArray[i]);
            }
        }

        MatOfDMatch newMatches = new MatOfDMatch();
        newMatches.fromList(matchesList);

        return newMatches;
    }

    /**
     * Merges the two given images by applying the given homography to the right image and returns
     * the result as a bitmap.
     *
     * @param imageL     The left image to merge.
     * @param imageR     The right image to merge.
     * @param homography The homography to apply to the right image.
     * @return The created bitmap of the merged images.
     */
    private Bitmap mergeImages(Mat imageL, Mat imageR, Mat homography)
    {
        Mat newImage = new Mat();

        Imgproc.warpPerspective(imageR, newImage, homography,
                                new Size(imageL.cols() + imageR.cols(), imageL.rows()));

        imageL.copyTo(new Mat(newImage, new Rect(0, 0, imageL.cols(), imageL.rows())));

        return createBitmap(newImage);
    }
}