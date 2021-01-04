/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * GridSearch.java
 * Copyright (C) 2006-2018 University of Waikato, Hamilton, New Zealand
 */

package weka.classifiers.meta;

import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.RandomizableSingleClassifierEnhancer;
import weka.core.AdditionalMeasureProducer;
import weka.core.Capabilities;
import weka.core.Capabilities.Capability;
import weka.core.Debug;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.PropertyPath;
import weka.core.RevisionHandler;
import weka.core.RevisionUtils;
import weka.core.SelectedTag;
import weka.core.SerializedObject;
import weka.core.Summarizable;
import weka.core.Tag;
import weka.core.Utils;
import weka.core.WekaException;
import weka.core.expressionlanguage.common.Primitives.DoubleExpression;
import weka.core.expressionlanguage.common.SimpleVariableDeclarations;
import weka.core.expressionlanguage.common.MacroDeclarationsCompositor;
import weka.core.expressionlanguage.common.MathFunctions;
import weka.core.expressionlanguage.common.IfElseMacro;
import weka.core.expressionlanguage.common.JavaMacro;
import weka.core.expressionlanguage.parser.Parser;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.MathExpression;
import weka.filters.unsupervised.instance.Resample;

/**
 * <!-- globalinfo-start --> Performs a grid search of parameter pairs for a
 * classifier and chooses the best pair found for the actual predicting.<br/>
 * <br/>
 * The initial grid is worked on with 2-fold CV to determine the values of the
 * parameter pairs for the selected type of evaluation (e.g., accuracy). The
 * best point in the grid is then taken and a 10-fold CV is performed with the
 * adjacent parameter pairs. If a better pair is found, then this will act as
 * new center and another 10-fold CV will be performed (kind of hill-climbing).
 * This process is repeated until no better pair is found or the best pair is on
 * the border of the grid.<br/>
 * In case the best pair is on the border, one can let GridSearch automatically
 * extend the grid and continue the search. Check out the properties
 * 'gridIsExtendable' (option '-extend-grid') and 'maxGridExtensions' (option
 * '-max-grid-extensions &lt;num&gt;').<br/>
 * <br/>
 * GridSearch can handle doubles, integers (values are just cast to int) and
 * booleans (0 is false, otherwise true). float, char and long are supported as
 * well.<br/>
 * <br/>
 * The best classifier setup can be accessed after the buildClassifier call via
 * the getBestClassifier methods.<br/><br/>
 * Note: with -num-slots/numExecutionSlots you can specify how many setups are
 * evaluated in parallel, taking advantage of multi-cpu/core architectures.
 * <p/>
 * <!-- globalinfo-end -->
 * 
 * <!-- options-start --> Valid options are:
 * <p/>
 * 
 * <pre>
 * -E &lt;CC|RMSE|RRSE|MAE|RAE|COMB|ACC|WAUC|KAP&gt;
 *  Determines the parameter used for evaluation:
 *  CC = Correlation coefficient
 *  RMSE = Root mean squared error
 *  RRSE = Root relative squared error
 *  MAE = Mean absolute error
 *  RAE = Root absolute error
 *  COMB = Combined = (1-abs(CC)) + RRSE + RAE
 *  ACC = Accuracy
 *  WAUC = Weighted AUC
 *  KAP = Kappa
 *  (default: CC)
 * </pre>
 * 
 * <pre>
 * -y-property &lt;option&gt;
 *  The Y option to test (without leading dash).
 *  (default: kernel.gamma)
 * </pre>
 * 
 * <pre>
 * -y-min &lt;num&gt;
 *  The minimum for Y.
 *  (default: -3)
 * </pre>
 * 
 * <pre>
 * -y-max &lt;num&gt;
 *  The maximum for Y.
 *  (default: +3)
 * </pre>
 * 
 * <pre>
 * -y-step &lt;num&gt;
 *  The step size for Y.
 *  (default: 1)
 * </pre>
 * 
 * <pre>
 * -y-base &lt;num&gt;
 *  The base for Y.
 *  (default: 10)
 * </pre>
 * 
 * <pre>
 * -y-expression &lt;expr&gt;
 *  The expression for Y.
 *  Available parameters:
 *   BASE
 *   FROM
 *   TO
 *   STEP
 *   I - the current iteration value
 *   (from 'FROM' to 'TO' with stepsize 'STEP')
 *  (default: 'pow(BASE,I)')
 * </pre>
 * 
 * <pre>
 * -x-property &lt;option&gt;
 *  The X option to test (without leading dash).
 *  (default: C)
 * </pre>
 * 
 * <pre>
 * -x-min &lt;num&gt;
 *  The minimum for X.
 *  (default: -3)
 * </pre>
 * 
 * <pre>
 * -x-max &lt;num&gt;
 *  The maximum for X.
 *  (default: 3)
 * </pre>
 * 
 * <pre>
 * -x-step &lt;num&gt;
 *  The step size for X.
 *  (default: 1)
 * </pre>
 * 
 * <pre>
 * -x-base &lt;num&gt;
 *  The base for X.
 *  (default: 10)
 * </pre>
 * 
 * <pre>
 * -x-expression &lt;expr&gt;
 *  The expression for the X value.
 *  Available parameters:
 *   BASE
 *   MIN
 *   MAX
 *   STEP
 *   I - the current iteration value
 *   (from 'FROM' to 'TO' with stepsize 'STEP')
 *  (default: 'pow(BASE,I)')
 * </pre>
 * 
 * <pre>
 * -extend-grid
 *  Whether the grid can be extended.
 *  (default: no)
 * </pre>
 * 
 * <pre>
 * -max-grid-extensions &lt;num&gt;
 *  The maximum number of grid extensions (-1 is unlimited).
 *  (default: 3)
 * </pre>
 * 
 * <pre>
 * -sample-size &lt;num&gt;
 *  The size (in percent) of the sample to search the inital grid with.
 *  (default: 100)
 * </pre>
 * 
 * <pre>
 * -traversal &lt;ROW-WISE|COLUMN-WISE&gt;
 *  The type of traversal for the grid.
 *  (default: COLUMN-WISE)
 * </pre>
 * 
 * <pre>
 * -log-file &lt;filename&gt;
 *  The log file to log the messages to.
 *  (default: none)
 * </pre>
 * 
 * <pre>
 * -num-slots &lt;num&gt;
 *  Number of execution slots.
 *  (default 1 - i.e. no parallelism)
 * </pre>
 * 
 * <pre>
 * -S &lt;num&gt;
 *  Random number seed.
 *  (default 1)
 * </pre>
 * 
 * <pre>
 * -W
 *  Full name of base classifier.
 *  (default: weka.classifiers.functions.SMOreg with options -K weka.classifiers.functions.supportVector.RBFKernel)
 * </pre>
 * 
 * <pre>
 * -output-debug-info
 *  If set, classifier is run in debug mode and
 *  may output additional info to the console
 * </pre>
 * 
 * <pre>
 * -do-not-check-capabilities
 *  If set, classifier capabilities are not checked before classifier is built
 *  (use with caution).
 * </pre>
 * 
 * <pre>
 * Options specific to classifier weka.classifiers.functions.SMOreg:
 * </pre>
 * 
 * <pre>
 * -C &lt;double&gt;
 *  The complexity constant C.
 *  (default 1)
 * </pre>
 * 
 * <pre>
 * -N
 *  Whether to 0=normalize/1=standardize/2=neither.
 *  (default 0=normalize)
 * </pre>
 * 
 * <pre>
 * -I &lt;classname and parameters&gt;
 *  Optimizer class used for solving quadratic optimization problem
 *  (default weka.classifiers.functions.supportVector.RegSMOImproved)
 * </pre>
 * 
 * <pre>
 * -K &lt;classname and parameters&gt;
 *  The Kernel to use.
 *  (default: weka.classifiers.functions.supportVector.PolyKernel)
 * </pre>
 * 
 * <pre>
 * -output-debug-info
 *  If set, classifier is run in debug mode and
 *  may output additional info to the console
 * </pre>
 * 
 * <pre>
 * -do-not-check-capabilities
 *  If set, classifier capabilities are not checked before classifier is built
 *  (use with caution).
 * </pre>
 * 
 * <pre>
 * Options specific to optimizer ('-I') weka.classifiers.functions.supportVector.RegSMOImproved:
 * </pre>
 * 
 * <pre>
 * -T &lt;double&gt;
 *  The tolerance parameter for checking the stopping criterion.
 *  (default 0.001)
 * </pre>
 * 
 * <pre>
 * -V
 *  Use variant 1 of the algorithm when true, otherwise use variant 2.
 *  (default true)
 * </pre>
 * 
 * <pre>
 * -P &lt;double&gt;
 *  The epsilon for round-off error.
 *  (default 1.0e-12)
 * </pre>
 * 
 * <pre>
 * -L &lt;double&gt;
 *  The epsilon parameter in epsilon-insensitive loss function.
 *  (default 1.0e-3)
 * </pre>
 * 
 * <pre>
 * -W &lt;double&gt;
 *  The random number seed.
 *  (default 1)
 * </pre>
 * 
 * <pre>
 * Options specific to kernel ('-K') weka.classifiers.functions.supportVector.RBFKernel:
 * </pre>
 * 
 * <pre>
 * -G &lt;num&gt;
 *  The Gamma parameter.
 *  (default: 0.01)
 * </pre>
 * 
 * <pre>
 * -C &lt;num&gt;
 *  The size of the cache (a prime number), 0 for full cache and 
 *  -1 to turn it off.
 *  (default: 250007)
 * </pre>
 * 
 * <pre>
 * -output-debug-info
 *  Enables debugging output (if available) to be printed.
 *  (default: off)
 * </pre>
 * 
 * <pre>
 * -no-checks
 *  Turns off all checks - use with caution!
 *  (default: checks on)
 * </pre>
 * 
 * <!-- options-end -->
 * 
 * Examples:
 * <ul>
 * <li>
 * <b>Optimizing SMO with RBFKernel (C and gamma)</b>
 * <ul>
 * <li>Set the evaluation to <i>Accuracy</i>.</li>
 * <li>Set <code>weka.classifiers.functions.SMO</code> as classifier with
 * <code>weka.classifiers.functions.supportVector.RBFKernel</code> as kernel.</li>
 * <li>Set the XProperty to "C", XMin to "1", XMax to "16", XStep to
 * "1" and the XExpression to "I". This will test the "C" parameter of SMO for
 * the values from 1 to 16.</li>
 * <li>Set the YProperty to "kernel.gamma", YMin to "-5", YMax to
 * "2", YStep to "1" YBase to "10" and YExpression to "pow(BASE,I)". This will
 * test the gamma of the RBFKernel with the values 10^-5, 10^-4,..,10^2.</li>
 * </ul>
 * </li>
 * <li>
 * <b>Optimizing PLSFilter with LinearRegression (# of components and ridge) -
 * default setup</b>
 * <ul>
 * <li>Set the evaluation to <i>Correlation coefficient</i>.</li>
 * <li>Set the classifier to <code>weka.classifiers.meta.FilteredClassifier</code>.</li>
 * <li>Set the filter in the classifier to
 * <code>weka.filters.supervised.attribute.PLSFilter</code>.</li>
 * <li>Set the base classifier in FilteredClassifier to <code>weka.classifiers.functions.LinearRegression</code>
 * and use no attribute selection and no elimination of colinear
 * attributes.</li>
 * <li>Set the XProperty to "filter.numComponents", XMin to "5", XMax to "20"
 * (this depends heavily on your dataset, should be no more than the number of
 * attributes!), XStep to "1" and XExpression to "I". This will test the number
 * of components the PLSFilter will produce from 5 to 20.</li>
 * <li>Set the YProperty to "classifier.ridge", YMin to "-10", YMax to "5",
 * YStep to "1" and YExpression to "pow(BASE,I)". This will try ridge parameters
 * from 10^-10 to 10^5.</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * General notes:
 * <ul>
 * <li>Turn the <i>debug</i> flag on in order to see some progress output in the
 * console</li>
 * <li>If you want to view the fitness landscape that GridSearch explores,
 * select a <i>log file</i>. This log will then contain Gnuplot data and a script
 * block for viewing the landscape. Just copy paste those blocks into files
 * named accordingly and run Gnuplot with them.</li>
 * </ul>
 * 
 * @author Bernhard Pfahringer (bernhard at cs dot waikato dot ac dot nz)
 * @author Geoff Holmes (geoff at cs dot waikato dot ac dot nz)
 * @author fracpete (fracpete at waikato dot ac dot nz)
 * @version $Revision$
 */
public class GridSearch extends RandomizableSingleClassifierEnhancer implements
  AdditionalMeasureProducer, Summarizable {

  /**
   * a serializable version of Point2D.Double.
   * 
   * @see java.awt.geom.Point2D.Double
   */
  protected static class PointDouble extends java.awt.geom.Point2D.Double
    implements Serializable, RevisionHandler {

    /** for serialization. */
    private static final long serialVersionUID = 7151661776161898119L;

    /**
     * the default constructor.
     * 
     * @param x the x value of the point
     * @param y the y value of the point
     */
    public PointDouble(double x, double y) {
      super(x, y);
    }

    /**
     * Determines whether or not two points are equal.
     * 
     * @param obj an object to be compared with this PointDouble
     * @return true if the object to be compared has the same values; false
     *         otherwise.
     */
    @Override
    public boolean equals(Object obj) {
      PointDouble pd;

      pd = (PointDouble) obj;

      return (Utils.eq(this.getX(), pd.getX()) && Utils.eq(this.getY(),
        pd.getY()));
    }

    /**
     * returns a string representation of the Point.
     * 
     * @return the point as string
     */
    @Override
    public String toString() {
      return super.toString().replaceAll(".*\\[", "[");
    }

    /**
     * Returns the revision string.
     * 
     * @return the revision
     */
    @Override
    public String getRevision() {
      return RevisionUtils.extract("$Revision$");
    }
  }

  /**
   * a serializable version of Point.
   * 
   * @see java.awt.Point
   */
  protected static class PointInt extends java.awt.Point implements
    Serializable, RevisionHandler {

    /** for serialization. */
    private static final long serialVersionUID = -5900415163698021618L;

    /**
     * the default constructor.
     * 
     * @param x the x value of the point
     * @param y the y value of the point
     */
    public PointInt(int x, int y) {
      super(x, y);
    }

    /**
     * returns a string representation of the Point.
     * 
     * @return the point as string
     */
    @Override
    public String toString() {
      return super.toString().replaceAll(".*\\[", "[");
    }

    /**
     * Returns the revision string.
     * 
     * @return the revision
     */
    @Override
    public String getRevision() {
      return RevisionUtils.extract("$Revision$");
    }
  }

  /**
   * for generating the parameter pairs in a grid.
   */
  protected static class Grid implements Serializable, RevisionHandler {

    /** for serialization. */
    private static final long serialVersionUID = 7290732613611243139L;

    /** the minimum on the X axis. */
    protected double m_MinX;

    /** the maximum on the X axis. */
    protected double m_MaxX;

    /** the step size for the X axis. */
    protected double m_StepX;

    /** the label for the X axis. */
    protected String m_LabelX;

    /** the minimum on the Y axis. */
    protected double m_MinY;

    /** the maximum on the Y axis. */
    protected double m_MaxY;

    /** the step size for the Y axis. */
    protected double m_StepY;

    /** the label for the Y axis. */
    protected String m_LabelY;

    /** the number of points on the X axis. */
    protected int m_Width;

    /** the number of points on the Y axis. */
    protected int m_Height;

    /**
     * initializes the grid.
     * 
     * @param minX the minimum on the X axis
     * @param maxX the maximum on the X axis
     * @param stepX the step size for the X axis
     * @param minY the minimum on the Y axis
     * @param maxY the maximum on the Y axis
     * @param stepY the step size for the Y axis
     */
    public Grid(double minX, double maxX, double stepX, double minY,
      double maxY, double stepY) {
      this(minX, maxX, stepX, "", minY, maxY, stepY, "");
    }

    /**
     * initializes the grid.
     * 
     * @param minX the minimum on the X axis
     * @param maxX the maximum on the X axis
     * @param stepX the step size for the X axis
     * @param labelX the label for the X axis
     * @param minY the minimum on the Y axis
     * @param maxY the maximum on the Y axis
     * @param stepY the step size for the Y axis
     * @param labelY the label for the Y axis
     */
    public Grid(double minX, double maxX, double stepX, String labelX,
      double minY, double maxY, double stepY, String labelY) {

      super();

      m_MinX = minX;
      m_MaxX = maxX;
      m_StepX = stepX;
      m_LabelX = labelX;
      m_MinY = minY;
      m_MaxY = maxY;
      m_StepY = stepY;
      m_LabelY = labelY;
      m_Height = (int) StrictMath.round((m_MaxY - m_MinY) / m_StepY) + 1;
      m_Width = (int) StrictMath.round((m_MaxX - m_MinX) / m_StepX) + 1;

      // is min < max?
      if (m_MinX >= m_MaxX) {
        throw new IllegalArgumentException("XMin must be smaller than XMax!");
      }
      if (m_MinY >= m_MaxY) {
        throw new IllegalArgumentException("YMin must be smaller than YMax!");
      }

      // steps positive?
      if (m_StepX <= 0) {
        throw new IllegalArgumentException("XStep must be a positive number!");
      }
      if (m_StepY <= 0) {
        throw new IllegalArgumentException("YStep must be a positive number!");
      }

      // check borders
      if (!Utils.eq(m_MinX + (m_Width - 1) * m_StepX, m_MaxX)) {
        throw new IllegalArgumentException(
          "X axis doesn't match! Provided max: " + m_MaxX
            + ", calculated max via min and step size: "
            + (m_MinX + (m_Width - 1) * m_StepX));
      }
      if (!Utils.eq(m_MinY + (m_Height - 1) * m_StepY, m_MaxY)) {
        throw new IllegalArgumentException(
          "Y axis doesn't match! Provided max: " + m_MaxY
            + ", calculated max via min and step size: "
            + (m_MinY + (m_Height - 1) * m_StepY));
      }
    }

    /**
     * Tests itself against the provided grid object.
     * 
     * @param o the grid object to compare against
     * @return if the two grids have the same setup
     */
    @Override
    public boolean equals(Object o) {
      boolean result;
      Grid g;

      g = (Grid) o;

      result = (width() == g.width()) && (height() == g.height())
        && (getMinX() == g.getMinX()) && (getMinY() == g.getMinY())
        && (getStepX() == g.getStepX()) && (getStepY() == g.getStepY())
        && getLabelX().equals(g.getLabelX())
        && getLabelY().equals(g.getLabelY());

      return result;
    }

    /**
     * returns the left border.
     * 
     * @return the left border
     */
    public double getMinX() {
      return m_MinX;
    }

    /**
     * returns the right border.
     * 
     * @return the right border
     */
    public double getMaxX() {
      return m_MaxX;
    }

    /**
     * returns the step size on the X axis.
     * 
     * @return the step size
     */
    public double getStepX() {
      return m_StepX;
    }

    /**
     * returns the label for the X axis.
     * 
     * @return the label
     */
    public String getLabelX() {
      return m_LabelX;
    }

    /**
     * returns the bottom border.
     * 
     * @return the bottom border
     */
    public double getMinY() {
      return m_MinY;
    }

    /**
     * returns the top border.
     * 
     * @return the top border
     */
    public double getMaxY() {
      return m_MaxY;
    }

    /**
     * returns the step size on the Y axis.
     * 
     * @return the step size
     */
    public double getStepY() {
      return m_StepY;
    }

    /**
     * returns the label for the Y axis.
     * 
     * @return the label
     */
    public String getLabelY() {
      return m_LabelY;
    }

    /**
     * returns the number of points in the grid on the Y axis (incl. borders)
     * 
     * @return the number of points in the grid on the Y axis
     */
    public int height() {
      return m_Height;
    }

    /**
     * returns the number of points in the grid on the X axis (incl. borders)
     * 
     * @return the number of points in the grid on the X axis
     */
    public int width() {
      return m_Width;
    }

    /**
     * returns the values at the given point in the grid.
     * 
     * @param x the x-th point on the X axis
     * @param y the y-th point on the Y axis
     * @return the value pair at the given position
     */
    public PointDouble getValues(int x, int y) {
      if (x >= width()) {
        throw new IllegalArgumentException("Index out of scope on X axis (" + x
          + " >= " + width() + ")!");
      }
      if (y >= height()) {
        throw new IllegalArgumentException("Index out of scope on Y axis (" + y
          + " >= " + height() + ")!");
      }

      return new PointDouble(m_MinX + m_StepX * x, m_MinY + m_StepY * y);
    }

    /**
     * returns the closest index pair for the given value pair in the grid.
     * 
     * @param values the values to get the indices for
     * @return the closest indices in the grid
     */
    public PointInt getLocation(PointDouble values) {
      PointInt result;
      int x;
      int y;
      double distance;
      double currDistance;
      int i;

      // determine x
      x = 0;
      distance = m_StepX;
      for (i = 0; i < width(); i++) {
        currDistance = StrictMath.abs(values.getX() - getValues(i, 0).getX());
        if (Utils.sm(currDistance, distance)) {
          distance = currDistance;
          x = i;
        }
      }

      // determine y
      y = 0;
      distance = m_StepY;
      for (i = 0; i < height(); i++) {
        currDistance = StrictMath.abs(values.getY() - getValues(0, i).getY());
        if (Utils.sm(currDistance, distance)) {
          distance = currDistance;
          y = i;
        }
      }

      result = new PointInt(x, y);
      return result;
    }

    /**
     * checks whether the given values are on the border of the grid.
     * 
     * @param values the values to check
     * @return true if the the values are on the border
     */
    public boolean isOnBorder(PointDouble values) {
      return isOnBorder(getLocation(values));
    }

    /**
     * checks whether the given location is on the border of the grid.
     * 
     * @param location the location to check
     * @return true if the the location is on the border
     */
    public boolean isOnBorder(PointInt location) {
      if (location.getX() == 0) {
        return true;
      } else if (location.getX() == width() - 1) {
        return true;
      }
      if (location.getY() == 0) {
        return true;
      } else if (location.getY() == height() - 1) {
        return true;
      } else {
        return false;
      }
    }

    /**
     * returns a subgrid with the same step sizes, but different borders.
     * 
     * @param top the top index
     * @param left the left index
     * @param bottom the bottom index
     * @param right the right index
     * @return the Sub-Grid
     */
    public Grid subgrid(int top, int left, int bottom, int right) {
      return new Grid(getValues(left, top).getX(),
        getValues(right, top).getX(), getStepX(), getLabelX(), getValues(left,
          bottom).getY(), getValues(left, top).getY(), getStepY(), getLabelY());
    }

    /**
     * returns an extended grid that encompasses the given point (won't be on
     * the border of the grid).
     * 
     * @param values the point that the grid should contain
     * @return the extended grid
     */
    public Grid extend(PointDouble values) {
      double minX;
      double maxX;
      double minY;
      double maxY;
      double distance;
      Grid result;

      // left
      if (Utils.smOrEq(values.getX(), getMinX())) {
        distance = getMinX() - values.getX();
        // exactly on grid point?
        if (Utils.eq(distance, 0)) {
          minX = getMinX() - getStepX()
            * (StrictMath.round(distance / getStepX()) + 1);
        } else {
          minX = getMinX() - getStepX()
            * (StrictMath.round(distance / getStepX()));
        }
      } else {
        minX = getMinX();
      }

      // right
      if (Utils.grOrEq(values.getX(), getMaxX())) {
        distance = values.getX() - getMaxX();
        // exactly on grid point?
        if (Utils.eq(distance, 0)) {
          maxX = getMaxX() + getStepX()
            * (StrictMath.round(distance / getStepX()) + 1);
        } else {
          maxX = getMaxX() + getStepX()
            * (StrictMath.round(distance / getStepX()));
        }
      } else {
        maxX = getMaxX();
      }

      // bottom
      if (Utils.smOrEq(values.getY(), getMinY())) {
        distance = getMinY() - values.getY();
        // exactly on grid point?
        if (Utils.eq(distance, 0)) {
          minY = getMinY() - getStepY()
            * (StrictMath.round(distance / getStepY()) + 1);
        } else {
          minY = getMinY() - getStepY()
            * (StrictMath.round(distance / getStepY()));
        }
      } else {
        minY = getMinY();
      }

      // top
      if (Utils.grOrEq(values.getY(), getMaxY())) {
        distance = values.getY() - getMaxY();
        // exactly on grid point?
        if (Utils.eq(distance, 0)) {
          maxY = getMaxY() + getStepY()
            * (StrictMath.round(distance / getStepY()) + 1);
        } else {
          maxY = getMaxY() + getStepY()
            * (StrictMath.round(distance / getStepY()));
        }
      } else {
        maxY = getMaxY();
      }

      result = new Grid(minX, maxX, getStepX(), getLabelX(), minY, maxY,
        getStepY(), getLabelY());

      // did the grid really extend?
      if (equals(result)) {
        throw new IllegalStateException("Grid extension failed!");
      }

      return result;
    }

    /**
     * returns an Enumeration over all pairs in the given row.
     * 
     * @param y the row to retrieve
     * @return an Enumeration over all pairs
     * @see #getValues(int, int)
     */
    public Enumeration<PointDouble> row(int y) {
      Vector<PointDouble> result;
      int i;

      result = new Vector<PointDouble>();

      for (i = 0; i < width(); i++) {
        result.add(getValues(i, y));
      }

      return result.elements();
    }

    /**
     * returns an Enumeration over all pairs in the given column.
     * 
     * @param x the column to retrieve
     * @return an Enumeration over all pairs
     * @see #getValues(int, int)
     */
    public Enumeration<PointDouble> column(int x) {
      Vector<PointDouble> result;
      int i;

      result = new Vector<PointDouble>();

      for (i = 0; i < height(); i++) {
        result.add(getValues(x, i));
      }

      return result.elements();
    }

    /**
     * returns a string representation of the grid.
     * 
     * @return a string representation
     */
    @Override
    public String toString() {
      String result;

      result = "X: " + m_MinX + " - " + m_MaxX + ", Step " + m_StepX;
      if (m_LabelX.length() != 0) {
        result += " (" + m_LabelX + ")";
      }
      result += "\n";

      result += "Y: " + m_MinY + " - " + m_MaxY + ", Step " + m_StepY;
      if (m_LabelY.length() != 0) {
        result += " (" + m_LabelY + ")";
      }
      result += "\n";

      result += "Dimensions (Rows x Columns): " + height() + " x " + width();

      return result;
    }

    /**
     * Returns the revision string.
     * 
     * @return the revision
     */
    @Override
    public String getRevision() {
      return RevisionUtils.extract("$Revision$");
    }
  }

  /**
   * A helper class for storing the performance of a values-pair. Can be sorted
   * with the PerformanceComparator class.
   * 
   * @see PerformanceComparator
   */
  protected static class Performance implements Serializable, RevisionHandler {

    /** for serialization. */
    private static final long serialVersionUID = -4374706475277588755L;

    /** the value pair the classifier was built with. */
    protected PointDouble m_Values;

    /** the Correlation coefficient. */
    protected double m_CC;

    /** the Root mean squared error. */
    protected double m_RMSE;

    /** the Root relative squared error. */
    protected double m_RRSE;

    /** the Mean absolute error. */
    protected double m_MAE;

    /** the Relative absolute error. */
    protected double m_RAE;

    /** the Accuracy. */
    protected double m_ACC;

    /** The weighted AUC value. */
    protected double m_wAUC;

    /** the kappa value. */
    protected double m_Kappa;

    /**
     * initializes the performance container.
     * 
     * @param values the values-pair
     * @param evaluation the evaluation to extract the performance measures from
     * @throws Exception if retrieving of measures fails
     */
    public Performance(PointDouble values, Evaluation evaluation)
      throws Exception {
      super();

      m_Values = values;

      m_RMSE = evaluation.rootMeanSquaredError();
      m_RRSE = evaluation.rootRelativeSquaredError();
      m_MAE = evaluation.meanAbsoluteError();
      m_RAE = evaluation.relativeAbsoluteError();

      try {
        m_wAUC = evaluation.weightedAreaUnderROC();
      } catch (Exception e) {
        m_wAUC = Double.NaN;
      }
      try {
        m_CC = evaluation.correlationCoefficient();
      } catch (Exception e) {
        m_CC = Double.NaN;
      }
      try {
        m_ACC = evaluation.pctCorrect();
      } catch (Exception e) {
        m_ACC = Double.NaN;
      }
      try {
        m_Kappa = evaluation.kappa();
      } catch (Exception e) {
        m_Kappa = Double.NaN;
      }
    }

    /**
     * returns the performance measure.
     * 
     * @param evaluation the type of measure to return
     * @return the performance measure
     */
    public double getPerformance(int evaluation) {
      double result;

      result = Double.NaN;

      switch (evaluation) {
      case EVALUATION_CC:
        result = m_CC;
        break;
      case EVALUATION_RMSE:
        result = m_RMSE;
        break;
      case EVALUATION_RRSE:
        result = m_RRSE;
        break;
      case EVALUATION_MAE:
        result = m_MAE;
        break;
      case EVALUATION_RAE:
        result = m_RAE;
        break;
      case EVALUATION_COMBINED:
        result = (1 - StrictMath.abs(m_CC)) + m_RRSE + m_RAE;
        break;
      case EVALUATION_ACC:
        result = m_ACC;
        break;
      case EVALUATION_KAPPA:
        result = m_Kappa;
        break;
      case EVALUATION_WAUC:
        result = m_wAUC;
        break;
      default:
        throw new IllegalArgumentException("Evaluation type '" + evaluation
          + "' not supported!");
      }

      return result;
    }

    /**
     * returns the values-pair for this performance.
     * 
     * @return the values-pair
     */
    public PointDouble getValues() {
      return m_Values;
    }

    /**
     * returns a string representation of this performance object.
     * 
     * @param evaluation the type of performance to return
     * @return a string representation
     */
    public String toString(int evaluation) {
      String result;

      result = "Performance (" + getValues() + "): "
        + getPerformance(evaluation) + " ("
        + new SelectedTag(evaluation, TAGS_EVALUATION) + ")";

      return result;
    }

    /**
     * returns a Gnuplot string of this performance object.
     * 
     * @param evaluation the type of performance to return
     * @return the gnuplot string (x, y, z)
     */
    public String toGnuplot(int evaluation) {
      String result;

      result = getValues().getX() + "\t" + getValues().getY() + "\t"
        + getPerformance(evaluation);

      return result;
    }

    /**
     * returns a string representation of this performance object.
     * 
     * @return a string representation
     */
    @Override
    public String toString() {
      String result;
      int i;

      result = "Performance (" + getValues() + "): ";

      for (i = 0; i < TAGS_EVALUATION.length; i++) {
        if (i > 0) {
          result += ", ";
        }
        result += getPerformance(TAGS_EVALUATION[i].getID()) + " ("
          + new SelectedTag(TAGS_EVALUATION[i].getID(), TAGS_EVALUATION) + ")";
      }

      return result;
    }

    /**
     * Returns the revision string.
     * 
     * @return the revision
     */
    @Override
    public String getRevision() {
      return RevisionUtils.extract("$Revision$");
    }
  }

  /**
   * A concrete Comparator for the Performance class.
   * 
   * @see Performance
   */
  protected static class PerformanceComparator implements
    Comparator<Performance>, Serializable, RevisionHandler {

    /** for serialization. */
    private static final long serialVersionUID = 6507592831825393847L;

    /**
     * the performance measure to use for comparison.
     * 
     * @see GridSearch#TAGS_EVALUATION
     */
    protected int m_Evaluation;

    /**
     * initializes the comparator with the given performance measure.
     * 
     * @param evaluation the performance measure to use
     * @see GridSearch#TAGS_EVALUATION
     */
    public PerformanceComparator(int evaluation) {
      super();

      m_Evaluation = evaluation;
    }

    /**
     * returns the performance measure that's used to compare the objects.
     * 
     * @return the performance measure
     * @see GridSearch#TAGS_EVALUATION
     */
    public int getEvaluation() {
      return m_Evaluation;
    }

    /**
     * Compares its two arguments for order. Returns a negative integer, zero,
     * or a positive integer as the first argument is less than, equal to, or
     * greater than the second.
     * 
     * @param o1 the first performance
     * @param o2 the second performance
     * @return the order
     */
    @Override
    public int compare(Performance o1, Performance o2) {
      int result;
      double p1;
      double p2;

      p1 = o1.getPerformance(getEvaluation());
      p2 = o2.getPerformance(getEvaluation());

      if (p1 < p2) {
        result = -1;
      } else if (p1 > p2) {
        result = 1;
      } else { // Need to make order deterministic
        if (o1.getValues().getX() < o2.getValues().getX()) {
          result = -1;
        } else if (o1.getValues().getX() > o2.getValues().getX()) {
          result = 1;
        } else {
          if (o1.getValues().getY() < o2.getValues().getY()) {
            result = -1;
          } else if (o1.getValues().getY() > o2.getValues().getY()) {
            result = 1;
          } else {
            result = 0;
          }
        }
      }

      // only correlation coefficient/accuracy/kappa obey to this order, for the
      // errors (and the combination of all three), the smaller the number the
      // better -> hence invert them
      if ((getEvaluation() != EVALUATION_CC)
        && (getEvaluation() != EVALUATION_ACC)
        && (getEvaluation() != EVALUATION_WAUC)
        && (getEvaluation() != EVALUATION_KAPPA)) {
        result = -result;
      }

      return result;
    }

    /**
     * Indicates whether some other object is "equal to" this Comparator.
     * 
     * @param obj the object to compare with
     * @return true if the same evaluation type is used
     */
    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof PerformanceComparator)) {
        throw new IllegalArgumentException("Must be PerformanceComparator!");
      }

      return (m_Evaluation == ((PerformanceComparator) obj).m_Evaluation);
    }

    /**
     * Returns the revision string.
     * 
     * @return the revision
     */
    @Override
    public String getRevision() {
      return RevisionUtils.extract("$Revision$");
    }
  }

  /**
   * Generates a 2-dim array for the performances from a grid for a certain
   * type. x-min/y-min is in the bottom-left corner, i.e., getTable()[0][0]
   * returns the performance for the x-min/y-max pair.
   * 
   * <pre>
   * x-min     x-max
   * |-------------|
   *                - y-max
   *                |
   *                |
   *                - y-min
   * </pre>
   */
  protected static class PerformanceTable implements Serializable,
    RevisionHandler {

    /** for serialization. */
    private static final long serialVersionUID = 5486491313460338379L;

    /** the owning classifier. */
    protected GridSearch m_Owner;

    /** the corresponding grid. */
    protected Grid m_Grid;

    /** the performances. */
    protected Vector<Performance> m_Performances;

    /** the type of performance the table was generated for. */
    protected int m_Type;

    /** the table with the values. */
    protected double[][] m_Table;

    /** the minimum performance. */
    protected double m_Min;

    /** the maximum performance. */
    protected double m_Max;

    /**
     * initializes the table.
     * 
     * @param owner the owning GridSearch
     * @param grid the underlying grid
     * @param performances the performances
     * @param type the type of performance
     */
    public PerformanceTable(GridSearch owner, Grid grid,
      Vector<Performance> performances, int type) {
      super();

      m_Owner = owner;
      m_Grid = grid;
      m_Type = type;
      m_Performances = performances;

      generate();
    }

    /**
     * generates the table.
     */
    protected void generate() {
      Performance perf;
      int i;
      PointInt location;

      m_Table = new double[getGrid().height()][getGrid().width()];
      m_Min = 0;
      m_Max = 0;

      for (i = 0; i < getPerformances().size(); i++) {
        perf = getPerformances().get(i);
        location = getGrid().getLocation(perf.getValues());
        m_Table[getGrid().height() - (int) location.getY() - 1][(int) location
          .getX()] = perf.getPerformance(getType());

        // determine min/max
        if (i == 0) {
          m_Min = perf.getPerformance(m_Type);
          m_Max = m_Min;
        } else {
          if (perf.getPerformance(m_Type) < m_Min) {
            m_Min = perf.getPerformance(m_Type);
          }
          if (perf.getPerformance(m_Type) > m_Max) {
            m_Max = perf.getPerformance(m_Type);
          }
        }
      }
    }

    /**
     * returns the corresponding grid.
     * 
     * @return the underlying grid
     */
    public Grid getGrid() {
      return m_Grid;
    }

    /**
     * returns the underlying performances.
     * 
     * @return the underlying performances
     */
    public Vector<Performance> getPerformances() {
      return m_Performances;
    }

    /**
     * returns the type of performance.
     * 
     * @return the type of performance
     */
    public int getType() {
      return m_Type;
    }

    /**
     * returns the generated table.
     * 
     * @return the performance table
     * @see #m_Table
     * @see #generate()
     */
    public double[][] getTable() {
      return m_Table;
    }

    /**
     * the minimum performance.
     * 
     * @return the performance
     */
    public double getMin() {
      return m_Min;
    }

    /**
     * the maximum performance.
     * 
     * @return the performance
     */
    public double getMax() {
      return m_Max;
    }

    /**
     * returns the table as string.
     * 
     * @return the table as string
     */
    @Override
    public String toString() {
      String result;
      int i;
      int n;

      result = "Table ("
        + new SelectedTag(getType(), TAGS_EVALUATION).getSelectedTag()
          .getReadable() + ") - " + "X: " + getGrid().getLabelX() + ", Y: "
        + getGrid().getLabelY() + ":\n";

      for (i = 0; i < getTable().length; i++) {
        if (i > 0) {
          result += "\n";
        }

        for (n = 0; n < getTable()[i].length; n++) {
          if (n > 0) {
            result += ",";
          }
          result += getTable()[i][n];
        }
      }

      return result;
    }

    /**
     * returns a string containing a gnuplot script+data file.
     * 
     * @return the data in gnuplot format
     */
    public String toGnuplot() {
      StringBuffer result;
      Tag type;
      int i;

      result = new StringBuffer();
      type = new SelectedTag(getType(), TAGS_EVALUATION).getSelectedTag();

      result.append("Gnuplot (" + type.getReadable() + "):\n");
      result.append("# begin 'gridsearch.data'\n");
      result.append("# " + type.getReadable() + "\n");
      for (i = 0; i < getPerformances().size(); i++) {
        result.append(getPerformances().get(i).toGnuplot(type.getID()) + "\n");
      }
      result.append("# end 'gridsearch.data'\n\n");

      result.append("# begin 'gridsearch.plot'\n");
      result.append("# " + type.getReadable() + "\n");
      result.append("set data style lines\n");
      result.append("set contour base\n");
      result.append("set surface\n");
      result.append("set title '" + m_Owner.getData().relationName() + "'\n");
      result.append("set xrange [" + getGrid().getMinX() + ":"
        + getGrid().getMaxX() + "]\n");
      result.append("set xlabel 'x ("
        + m_Owner.getClassifier().getClass().getName() + ": "
        + m_Owner.getXProperty() + ")'\n");
      result.append("set yrange [" + getGrid().getMinY() + ":"
        + getGrid().getMaxY() + "]\n");
      result.append("set ylabel 'y - ("
        + m_Owner.getClassifier().getClass().getName() + ": "
        + m_Owner.getYProperty() + ")'\n");
      result.append("set zrange [" + (getMin() - (getMax() - getMin()) * 0.1)
        + ":" + (getMax() + (getMax() - getMin()) * 0.1) + "]\n");
      result.append("set zlabel 'z - " + type.getReadable() + "'\n");
      result.append("set dgrid3d " + getGrid().height() + ","
        + getGrid().width() + ",1\n");
      result.append("show contour\n");
      result.append("splot 'gridsearch.data'\n");
      result.append("pause -1\n");
      result.append("# end 'gridsearch.plot'");

      return result.toString();
    }

    /**
     * Returns the revision string.
     * 
     * @return the revision
     */
    @Override
    public String getRevision() {
      return RevisionUtils.extract("$Revision$");
    }
  }

  /**
   * Represents a simple cache for performance objects.
   */
  protected static class PerformanceCache implements Serializable,
    RevisionHandler {

    /** for serialization. */
    private static final long serialVersionUID = 5838863230451530252L;

    /** the cache for points in the grid that got calculated. */
    protected Hashtable<String, Performance> m_Cache =
      new Hashtable<String, Performance>();

    /**
     * returns the ID string for a cache item.
     * 
     * @param cv the number of folds in the cross-validation
     * @param values the point in the grid
     * @return the ID string
     */
    protected String getID(int cv, PointDouble values) {
      return cv + "\t" + values.getX() + "\t" + values.getY();
    }

    /**
     * checks whether the point was already calculated ones.
     * 
     * @param cv the number of folds in the cross-validation
     * @param values the point in the grid
     * @return true if the value is already cached
     */
    public boolean isCached(int cv, PointDouble values) {
      return (get(cv, values) != null);
    }

    /**
     * returns a cached performance object, null if not yet in the cache.
     * 
     * @param cv the number of folds in the cross-validation
     * @param values the point in the grid
     * @return the cached performance item, null if not in cache
     */
    public Performance get(int cv, PointDouble values) {
      return m_Cache.get(getID(cv, values));
    }

    /**
     * adds the performance to the cache.
     * 
     * @param cv the number of folds in the cross-validation
     * @param p the performance object to store
     */
    public void add(int cv, Performance p) {
      m_Cache.put(getID(cv, p.getValues()), p);
    }

    /**
     * returns a string representation of the cache.
     * 
     * @return the string representation of the cache
     */
    @Override
    public String toString() {
      return m_Cache.toString();
    }

    /**
     * Returns the revision string.
     * 
     * @return the revision
     */
    @Override
    public String getRevision() {
      return RevisionUtils.extract("$Revision$");
    }
  }

  /**
   * Helper class for generating the setups.
   */
  protected static class SetupGenerator implements Serializable,
    RevisionHandler {

    /** for serialization. */
    private static final long serialVersionUID = -2517395033342543417L;
    
    /** variables exposed to expressions. */
    private static final SimpleVariableDeclarations variables =
        new SimpleVariableDeclarations();
    
    static {
        variables.addDouble("BASE");
        variables.addDouble("FROM");
        variables.addDouble("TO");
        variables.addDouble("STEP");
        variables.addDouble("I");
    }
    
    /** the owner. */
    protected GridSearch m_Owner;

    /** the Y option to work on. */
    protected String m_Y_Property;

    /** the minimum of Y. */
    protected double m_Y_Min;

    /** the maximum of Y. */
    protected double m_Y_Max;

    /** the step size of Y. */
    protected double m_Y_Step;

    /** the base for Y. */
    protected double m_Y_Base;

    /** The expression for the Y property. */
    protected String m_Y_Expression;

    /** the compiled expression for the Y property. */
    private DoubleExpression m_Y_Node;

    /** the X option to work on. */
    protected String m_X_Property;

    /** the minimum of X. */
    protected double m_X_Min;

    /** the maximum of X. */
    protected double m_X_Max;

    /** the step size of X. */
    protected double m_X_Step;

    /** the base for X. */
    protected double m_X_Base;

    /** the compiled expression for the X property. */
    private DoubleExpression m_X_Node;

    /** The expression for the X property. */
    protected String m_X_Expression;

    /**
     * Initializes the setup generator.
     * 
     * @param owner the owning classifier
     */
    public SetupGenerator(GridSearch owner) {
      super();

      m_Owner = owner;

      m_Y_Expression = m_Owner.getYExpression();
      m_Y_Property = m_Owner.getYProperty();
      m_Y_Min = m_Owner.getYMin();
      m_Y_Max = m_Owner.getYMax();
      m_Y_Step = m_Owner.getYStep();
      m_Y_Base = m_Owner.getYBase();

      // precompile expression
      try {
        m_Y_Node = (DoubleExpression) Parser.parse(
          // expression
          m_Y_Expression,
          // variables
          variables,
          // macros
          new MacroDeclarationsCompositor(
            new MathFunctions(),
            new IfElseMacro(),
            new JavaMacro()
            )
          );
      } catch (Exception e) {
        m_Y_Node = null;
        System.err.println("Failed to compile Y expression '"
          + m_Y_Expression + "'");
        e.printStackTrace();
      }

      m_X_Expression = m_Owner.getXExpression();
      m_X_Property = m_Owner.getXProperty();
      m_X_Min = m_Owner.getXMin();
      m_X_Max = m_Owner.getXMax();
      m_X_Step = m_Owner.getXStep();
      m_X_Base = m_Owner.getXBase();

      try {
        // precompile expression
        m_X_Node = (DoubleExpression) Parser.parse(
          // expression
          m_X_Expression,
          // variables
          variables,
          // macros
          new MacroDeclarationsCompositor(
            new MathFunctions(),
            new IfElseMacro(),
            new JavaMacro()
            )
          );
      } catch (Exception e) {
        m_X_Node = null;
        System.err.println("Failed to compile X expression '"
          + m_X_Expression + "'");
        e.printStackTrace();
      }

    }

    /**
     * evalutes the expression for the current iteration.
     * 
     * @param value the current iteration value (from 'min' to 'max' with
     *          stepsize 'step')
     * @param isX true if X is to be evaluated otherwise Y
     * @return the generated value, NaN if the evaluation fails
     */
    public double evaluate(double value, boolean isX) {
      
      DoubleExpression expr;

      if (isX) {
        if (variables.getInitializer().hasVariable("BASE"))
          variables.getInitializer().setDouble("BASE", m_X_Base);
        if (variables.getInitializer().hasVariable("FROM"))
          variables.getInitializer().setDouble("FROM", m_X_Min);
        if (variables.getInitializer().hasVariable("TO"))
          variables.getInitializer().setDouble("TO", m_X_Max);
        if (variables.getInitializer().hasVariable("STEP"))
          variables.getInitializer().setDouble("STEP", m_X_Step);
        expr = m_X_Node;
      } else {
        if (variables.getInitializer().hasVariable("BASE"))
          variables.getInitializer().setDouble("BASE", m_Y_Base);
        if (variables.getInitializer().hasVariable("FROM"))
          variables.getInitializer().setDouble("FROM", m_Y_Min);
        if (variables.getInitializer().hasVariable("TO"))
          variables.getInitializer().setDouble("TO", m_Y_Max);
        if (variables.getInitializer().hasVariable("STEP"))
          variables.getInitializer().setDouble("STEP", m_Y_Step);
        expr = m_Y_Node;
      }
      if (variables.getInitializer().hasVariable("I"))
        variables.getInitializer().setDouble("I", value);

      try {
        return expr.evaluate();
      } catch (Exception e) {
        e.printStackTrace();
        return Double.NaN;
      }
    }

    /**
     * tries to set the value as double, integer (just casts it to int!) or
     * boolean (false if 0, otherwise true) in the object according to the
     * specified path. float, char and long are also supported.
     * 
     * @param o the object to modify
     * @param path the property path
     * @param value the value to set
     * @return the modified object
     * @throws Exception if neither double nor int could be set
     */
    public Object setValue(Object o, String path, double value)
      throws Exception {
      PropertyDescriptor desc;
      Class<?> c;

      desc = PropertyPath.getPropertyDescriptor(o, path);
      if (desc == null) {
        throw new IllegalArgumentException("Failed to set property " + path + " on object " + o.getClass().getName());
      }
      c = desc.getPropertyType();

      // float
      if ((c == Float.class) || (c == Float.TYPE)) {
        PropertyPath.setValue(o, path, new Float((float) value));
      } else if ((c == Double.class) || (c == Double.TYPE)) {
        PropertyPath.setValue(o, path, new Double(value));
      } else if ((c == Character.class) || (c == Character.TYPE)) {
        PropertyPath.setValue(o, path, new Integer((char) value));
      } else if ((c == Integer.class) || (c == Integer.TYPE)) {
        PropertyPath.setValue(o, path, new Integer((int) value));
      } else if ((c == Long.class) || (c == Long.TYPE)) {
        PropertyPath.setValue(o, path, new Long((long) value));
      } else if ((c == Boolean.class) || (c == Boolean.TYPE)) {
        PropertyPath.setValue(o, path, (value == 0 ? new Boolean(false)
          : new Boolean(true)));
      } else {
        throw new Exception(
          "Could neither set double nor integer nor boolean value for '" + path
            + "'!");
      }

      return o;
    }

    /**
     * returns a fully configured object (a copy of the provided one).
     * 
     * @param original the object to create a copy from and set the parameters
     * @param valueX the current iteration value for X
     * @param valueY the current iteration value for Y
     * @return the configured classifier
     * @throws Exception if setup fails
     */
    public Object setup(Object original, double valueX, double valueY)
      throws Exception {
      Object result;

      result = new SerializedObject(original).getObject();

      if (original instanceof Classifier) {
        setValue(result, m_X_Property, valueX);

        setValue(result, m_Y_Property, valueY);
      } else {
        throw new IllegalArgumentException(
          "Object must be a classifier!");
      }

      return result;
    }

    /**
     * Returns the revision string.
     * 
     * @return the revision
     */
    @Override
    public String getRevision() {
      return RevisionUtils.extract("$Revision$");
    }
  }

  /**
   * Helper class for evaluating a setup.
   */
  protected static class EvaluationTask implements Callable, RevisionHandler {

    /** the owner. */
    protected GridSearch m_Owner;

    /** for generating the setups. */
    protected SetupGenerator m_Generator;

    /** the classifier to use. */
    protected Classifier m_Classifier;

    /** the data to use for training. */
    protected Instances m_Data;

    /** the values to use. */
    protected PointDouble m_Values;

    /** the number of folds for cross-validation. */
    protected int m_Folds;

    /** the type of evaluation. */
    protected int m_Evaluation;

    /**
     * Initializes the task.
     * 
     * @param owner the owning GridSearch classifier
     * @param generator the generator for the setips
     * @param inst the data
     * @param values the values in the grid
     * @param folds the number of cross-validation folds
     * @param eval the type of evaluation
     */
    public EvaluationTask(GridSearch owner, SetupGenerator generator,
      Instances inst, PointDouble values, int folds, int eval) {

      super();

      m_Owner = owner;
      m_Generator = generator;
      m_Classifier = m_Owner.getClassifier();
      m_Data = inst;
      m_Values = values;
      m_Folds = folds;
      m_Evaluation = eval;
    }

    /**
     * Performs the evaluation.
     */
    @Override
    public Exception call() {
      Evaluation eval;
      Classifier classifier;
      Performance performance;
      double x;
      double y;
      Instances data;

      classifier = null;
      x = m_Generator.evaluate(m_Values.getX(), true);
      y = m_Generator.evaluate(m_Values.getY(), false);
      try {

        data = m_Data;

        // setup classifier
        classifier = (Classifier) m_Generator.setup(m_Classifier, x, y);

        // evaluate
        eval = new Evaluation(data);
        eval.crossValidateModel(classifier, data, m_Folds,
          new Random(m_Owner.getSeed()));

        // store performance
        performance = new Performance(m_Values, eval);
        m_Owner.addPerformance(performance, m_Folds);

        // log
        m_Owner.log(performance + ": cached=false");

        // release slot
        m_Owner.completedEvaluation(classifier, null);

        // clean up
        m_Owner = null;
        m_Data = null;

        return null;

      } catch (Exception e) {
        if (m_Owner.getDebug()) {
          System.err
            .println("Encountered exception while evaluating classifier, skipping!");
          System.err.println("- Values....: " + m_Values);
          System.err.println("- Classifier: "
            + ((classifier != null) ? Utils.toCommandLine(classifier)
              : "-no setup-"));
          e.printStackTrace();
        }
        m_Owner.completedEvaluation(m_Values, e);


        // clean up
        m_Owner = null;
        m_Data = null;

        return e;
      }
    }

    /**
     * Returns the revision string.
     * 
     * @return the revision
     */
    @Override
    public String getRevision() {
      return RevisionUtils.extract("$Revision$");
    }
  }

  /** for serialization. */
  private static final long serialVersionUID = -3034773968581595348L;

  /** evaluation via: Correlation coefficient. */
  public static final int EVALUATION_CC = 0;
  /** evaluation via: Root mean squared error. */
  public static final int EVALUATION_RMSE = 1;
  /** evaluation via: Root relative squared error. */
  public static final int EVALUATION_RRSE = 2;
  /** evaluation via: Mean absolute error. */
  public static final int EVALUATION_MAE = 3;
  /** evaluation via: Relative absolute error. */
  public static final int EVALUATION_RAE = 4;
  /** evaluation via: Combined = (1-CC) + RRSE + RAE. */
  public static final int EVALUATION_COMBINED = 5;
  /** evaluation via: Accuracy. */
  public static final int EVALUATION_ACC = 6;
  /** evaluation via: kappa statistic. */
  public static final int EVALUATION_KAPPA = 7;
  /** evaluation via: weighted AUC */
  public static final int EVALUATION_WAUC = 8;
  /** evaluation. */
  public static final Tag[] TAGS_EVALUATION =
  {
    new Tag(EVALUATION_CC, "CC", "Correlation coefficient"),
    new Tag(EVALUATION_RMSE, "RMSE", "Root mean squared error"),
    new Tag(EVALUATION_RRSE, "RRSE", "Root relative squared error"),
    new Tag(EVALUATION_MAE, "MAE", "Mean absolute error"),
    new Tag(EVALUATION_RAE, "RAE", "Root absolute error"),
    new Tag(EVALUATION_COMBINED, "COMB",
      "Combined = (1-abs(CC)) + RRSE + RAE"),
    new Tag(EVALUATION_ACC, "ACC", "Accuracy"),
    new Tag(EVALUATION_WAUC, "WAUC", "Weighted AUC"),
    new Tag(EVALUATION_KAPPA, "KAP", "Kappa") };

  /** row-wise grid traversal. */
  public static final int TRAVERSAL_BY_ROW = 0;
  /** column-wise grid traversal. */
  public static final int TRAVERSAL_BY_COLUMN = 1;
  /** traversal. */
  public static final Tag[] TAGS_TRAVERSAL = {
    new Tag(TRAVERSAL_BY_ROW, "row-wise", "row-wise"),
    new Tag(TRAVERSAL_BY_COLUMN, "column-wise", "column-wise") };

  /** the Classifier with the best setup. */
  protected Classifier m_BestClassifier;

  /** the best values. */
  protected PointDouble m_Values = null;

  /** the type of evaluation. */
  protected int m_Evaluation = EVALUATION_CC;

  /**
   * the Y option to work on (without leading dash).
   */
  protected String m_Y_Property = "kernel.gamma";

  /** the minimum of Y. */
  protected double m_Y_Min = -3;

  /** the maximum of Y. */
  protected double m_Y_Max = +3;

  /** the step size of Y. */
  protected double m_Y_Step = 1;

  /** the base for Y. */
  protected double m_Y_Base = 10;

  /**
   * The expression for the Y property. Available parameters for the expression:
   * <ul>
   * <li>BASE</li>
   * <li>FROM (= min)</li>
   * <li>TO (= max)</li>
   * <li>STEP</li>
   * <li>I - the current value (from 'from' to 'to' with stepsize 'step')</li>
   * </ul>
   * 
   * @see MathExpression
   */
  protected String m_Y_Expression = "pow(BASE,I)";

  /**
   * the X option to work on (without leading dash)
   */
  protected String m_X_Property = "C";

  /** the minimum of X. */
  protected double m_X_Min = -3;

  /** the maximum of X. */
  protected double m_X_Max = 3;

  /** the step size of X. */
  protected double m_X_Step = 1;

  /** the base for X. */
  protected double m_X_Base = 10;

  /**
   * The expression for the X property. Available parameters for the expression:
   * <ul>
   * <li>BASE</li>
   * <li>FROM (= min)</li>
   * <li>TO (= max)</li>
   * <li>STEP</li>
   * <li>I - the current value (from 'from' to 'to' with stepsize 'step')</li>
   * </ul>
   * 
   * @see MathExpression
   */
  protected String m_X_Expression = "pow(BASE,I)";

  /** whether the grid can be extended. */
  protected boolean m_GridIsExtendable = false;

  /** maximum number of grid extensions (-1 means unlimited). */
  protected int m_MaxGridExtensions = 3;

  /** the number of extensions performed. */
  protected int m_GridExtensionsPerformed = 0;

  /** the sample size to search the initial grid with. */
  protected double m_SampleSize = 100;

  /** the traversal. */
  protected int m_Traversal = TRAVERSAL_BY_COLUMN;

  /** the log file to use. */
  protected File m_LogFile = new File(System.getProperty("user.dir"));

  /** the value-pairs grid. */
  protected Grid m_Grid;

  /** the training data. */
  protected Instances m_Data;

  /** the cache for points in the grid that got calculated. */
  protected PerformanceCache m_Cache;

  /** for storing the performances. */
  protected Vector<Performance> m_Performances;

  /** whether all performances in the grid are the same. */
  protected boolean m_UniformPerformance = false;

  /** The number of threads to have executing at any one time. */
  protected int m_NumExecutionSlots = 1;

  /** Pool of threads to train models with. */
  protected transient ExecutorService m_ExecutorPool;

  /** The number of setups completed so far. */
  protected int m_Completed;

  /**
   * The number of setups that experienced a failure of some sort during
   * construction.
   */
  protected int m_Failed;

  /** the number of setups to evaluate. */
  protected int m_NumSetups;

  /** the generator for generating the setups. */
  protected SetupGenerator m_Generator;

  /** for storing an exception that happened in one of the worker threads. */
  protected transient Exception m_Exception;

  /** The properties file containing default settings */
  protected static final String PROPERTY_FILE =
    "weka/classifiers/meta/GridSearch.props";

  /** The properties object holding the loaded defaults */
  protected static Properties GRID_SEARCH_PROPS;

  static {
    try {
      GRID_SEARCH_PROPS =
        Utils.readProperties("weka/classifiers/meta/GridSearch.props", GridSearch.class.getClassLoader());
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  /**
   * the default constructor.
   */
  public GridSearch() {
    super();

    defaultsFromProps();

    try {
      m_BestClassifier = AbstractClassifier.makeCopy(m_Classifier);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Set defaults for all options from a properties file. A default properties
   * file is included in the gridSearch jar file
   * (weka/classifiers/meta/GridSearch.props) - this can be copied, altered and
   * placed into ${WEKA_HOME}/props
   */
  protected void defaultsFromProps() {
    try {
      if (GRID_SEARCH_PROPS != null) {
        String classifierSpec = GRID_SEARCH_PROPS.getProperty("classifier");
        if (classifierSpec != null && classifierSpec.length() > 0) {
          String[] spec = Utils.splitOptions(classifierSpec);
          String classifier = spec[0];
          spec[0] = "";
          boolean ok = true;
          try {
            Classifier result = AbstractClassifier.forName(classifier, spec);
            setClassifier(result);

            // continue with the remaining defaults
            String yProp = GRID_SEARCH_PROPS.getProperty("yProperty", "");
            String yMin = GRID_SEARCH_PROPS.getProperty("yMin", "");
            String yMax = GRID_SEARCH_PROPS.getProperty("yMax", "");
            String yStep = GRID_SEARCH_PROPS.getProperty("yStep", "");
            String yBase = GRID_SEARCH_PROPS.getProperty("yBase", "");
            String yExpression =
              GRID_SEARCH_PROPS.getProperty("yExpression", "");
            if (yProp.length() > 0 && yMin.length() > 0 && yMax.length() > 0
              && yStep.length() > 0 && yBase.length() > 0
              && yExpression.length() > 0) {
              setYProperty(yProp);
              setYMin(Double.parseDouble(yMin));
              setYMax(Double.parseDouble(yMax));
              setYStep(Double.parseDouble(yStep));
              setYBase(Double.parseDouble(yBase));
              setYExpression(yExpression);
            } else {
              ok = false;
            }

            String xProp = GRID_SEARCH_PROPS.getProperty("xProperty", "");
            String xMin = GRID_SEARCH_PROPS.getProperty("xMin", "");
            String xMax = GRID_SEARCH_PROPS.getProperty("xMax", "");
            String xStep = GRID_SEARCH_PROPS.getProperty("xStep", "");
            String xBase = GRID_SEARCH_PROPS.getProperty("xBase", "");
            String xExpression =
              GRID_SEARCH_PROPS.getProperty("xExpression", "");
            if (xProp.length() > 0 && xMin.length() > 0 && xMax.length() > 0
              && xStep.length() > 0 && xBase.length() > 0
              && xExpression.length() > 0) {
              setXProperty(xProp);
              setXMin(Double.parseDouble(xMin));
              setXMax(Double.parseDouble(xMax));
              setXStep(Double.parseDouble(xStep));
              setXBase(Double.parseDouble(xBase));
              setXExpression(xExpression);
            } else {
              ok = false;
            }

            // optionals
            String gridExtend =
              GRID_SEARCH_PROPS.getProperty("gridIsExtendable", "false");
            setGridIsExtendable(Boolean.parseBoolean(gridExtend));
            String maxExtensions =
              GRID_SEARCH_PROPS.getProperty("maxGridExtensions", "3");
            setMaxGridExtensions(Integer.parseInt(maxExtensions));
            String sampleSizePerc =
              GRID_SEARCH_PROPS.getProperty("sampleSizePercent", "100");
            setSampleSizePercent(Integer.parseInt(sampleSizePerc));
            String traversal = GRID_SEARCH_PROPS.getProperty("traversal", "0");
            m_Traversal = Integer.parseInt(traversal);
            String eval = GRID_SEARCH_PROPS.getProperty("evaluation", "0");
            m_Evaluation = Integer.parseInt(eval);
            String numSlots = GRID_SEARCH_PROPS.getProperty("numSlots", "1");
            setNumExecutionSlots(Integer.parseInt(numSlots));
          } catch (Exception ex) {
            // continue with the default of GaussianProcesses
            ok = false;
          }

          if (!ok) {
            // setup GaussianProcesses
            setClassifier(new weka.classifiers.functions.GaussianProcesses());
            setYProperty("kernel.exponent");
            setYMin(1);
            setYMax(5);
            setYStep(1);
            setYBase(10);
            setYExpression("I");

            setXProperty("noise");
            setXMin(0.2);
            setXMax(2);
            setXStep(0.2);
            setXBase(10);
            setXExpression("I");
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Returns a string describing classifier.
   * 
   * @return a description suitable for displaying in the explorer/experimenter
   *         gui
   */
  public String globalInfo() {
    return "Performs a grid search of parameter pairs for a classifier and chooses the best "
      + "pair found for the actual predicting.\n\n"
      + "The initial grid is worked on with 2-fold CV to determine the values "
      + "of the parameter pairs for the selected type of evaluation (e.g., "
      + "accuracy). The best point in the grid is then taken and a 10-fold CV "
      + "is performed with the adjacent parameter pairs. If a better pair is "
      + "found, then this will act as new center and another 10-fold CV will "
      + "be performed (kind of hill-climbing). This process is repeated until "
      + "no better pair is found or the best pair is on the border of the grid.\n"
      + "In case the best pair is on the border, one can let GridSearch "
      + "automatically extend the grid and continue the search. Check out the "
      + "properties 'gridIsExtendable' (option '-extend-grid') and "
      + "'maxGridExtensions' (option '-max-grid-extensions <num>').\n\n"
      + "GridSearch can handle doubles, integers (values are just cast to int) "
      + "and booleans (0 is false, otherwise true). float, char and long are "
      + "supported as well.\n\n"
      + "The best classifier setup can be accessed after the buildClassifier "
      + "call via the getBestClassifier methods.\n\n"
      + "Note: with -num-slots/numExecutionSlots you can specify how many "
      + "setups are evaluated in parallel, taking advantage of multi-cpu/core "
      + "architectures.";
  }

  /**
   * Gets the classifier specification string, which contains the class name of
   * the classifier and any options to the classifier.
   * 
   * @return the classifier string.
   */
  @Override
  protected String getClassifierSpec() {

    Classifier c = getClassifier();
    if (c instanceof OptionHandler) {
      return c.getClass().getName() + " "
        + Utils.joinOptions(((OptionHandler) c).getOptions());
    }
    return c.getClass().getName();
  }

  /**
   * String describing default classifier.
   * 
   * @return the classname of the default classifier
   */
  @Override
  protected String defaultClassifierString() {
    try {
      if (GRID_SEARCH_PROPS != null) {
        String classifierSpec = GRID_SEARCH_PROPS.getProperty("classifier");
        if (classifierSpec != null && classifierSpec.length() > 0) {
          String[] parts = classifierSpec.split(" ");
          if (parts.length > 0) {
            return parts[0].trim();
          }
        }
      }
    } catch (Exception ex) {
      // don't complain here
    }

    return "weka.classifiers.functions.GaussianProcesses";
  }

  /**
   * String array with default classifier options.
   * 
   * @return string array with default classifier options
   */
  @Override
  protected String[] defaultClassifierOptions() {
    try {
      if (GRID_SEARCH_PROPS != null) {
        String classifierSpec = GRID_SEARCH_PROPS.getProperty("classifier");
        if (classifierSpec != null && classifierSpec.length() > 0) {

          String[] parts = Utils.splitOptions(classifierSpec);
          if (parts.length > 1) {
            // return Utils.splitOptions(parts[1]);
            parts[0] = "";
            return parts;
          }
        }
      }
    } catch (Exception ex) {
      // don't complain here
    }

    String[] opts =
    // { "-K", "weka.classifiers.functions.supportVector.RBFKernel" };
      {};
    return opts;
  }

  /**
   * Gets an enumeration describing the available options.
   * 
   * @return an enumeration of all the available options.
   */
  @Override
  public Enumeration<Option> listOptions() {

    Vector<Option> result = new Vector<Option>();

    String desc = "";
    for (Tag element : TAGS_EVALUATION) {
      SelectedTag tag = new SelectedTag(element.getID(), TAGS_EVALUATION);
      desc += "\t" + tag.getSelectedTag().getIDStr() + " = "
        + tag.getSelectedTag().getReadable() + "\n";
    }
    result.addElement(new Option(
      "\tDetermines the parameter used for evaluation:\n" + desc
        + "\t(default: " + new SelectedTag(EVALUATION_CC, TAGS_EVALUATION)
        + ")", "E", 1, "-E " + Tag.toOptionList(TAGS_EVALUATION)));

    result
      .addElement(new Option("\tThe Y option to test (without leading dash).\n"
        + "\t(default: kernel.gamma)", "y-property", 1,
        "-y-property <option>"));

    result.addElement(new Option("\tThe minimum for Y.\n" + "\t(default: -3)",
      "y-min", 1, "-y-min <num>"));

    result.addElement(new Option("\tThe maximum for Y.\n" + "\t(default: +3)",
      "y-max", 1, "-y-max <num>"));

    result.addElement(new Option("\tThe step size for Y.\n" + "\t(default: 1)",
      "y-step", 1, "-y-step <num>"));

    result.addElement(new Option("\tThe base for Y.\n" + "\t(default: 10)",
      "y-base", 1, "-y-base <num>"));

    result
      .addElement(new Option("\tThe expression for Y.\n"
        + "\tAvailable parameters:\n" + "\t\tBASE\n" + "\t\tFROM\n"
        + "\t\tTO\n" + "\t\tSTEP\n" + "\t\tI - the current iteration value\n"
        + "\t\t(from 'FROM' to 'TO' with stepsize 'STEP')\n"
        + "\t(default: 'pow(BASE,I)')", "y-expression", 1,
        "-y-expression <expr>"));

    result.addElement(new Option(
      "\tThe X option to test (without leading dash).\n" + "\t(default: "
        + "C)", "x-property", 1,
      "-x-property <option>"));

    result.addElement(new Option("\tThe minimum for X.\n" + "\t(default: -3)",
      "x-min", 1, "-x-min <num>"));

    result.addElement(new Option("\tThe maximum for X.\n" + "\t(default: 3)",
      "x-max", 1, "-x-max <num>"));

    result.addElement(new Option("\tThe step size for X.\n" + "\t(default: 1)",
      "x-step", 1, "-x-step <num>"));

    result.addElement(new Option("\tThe base for X.\n" + "\t(default: 10)",
      "x-base", 1, "-x-base <num>"));

    result
      .addElement(new Option("\tThe expression for the X value.\n"
        + "\tAvailable parameters:\n" + "\t\tBASE\n" + "\t\tMIN\n"
        + "\t\tMAX\n" + "\t\tSTEP\n" + "\t\tI - the current iteration value\n"
        + "\t\t(from 'FROM' to 'TO' with stepsize 'STEP')\n"
        + "\t(default: 'pow(BASE,I)')", "x-expression", 1,
        "-x-expression <expr>"));

    result.addElement(new Option("\tWhether the grid can be extended.\n"
      + "\t(default: no)", "extend-grid", 0, "-extend-grid"));

    result.addElement(new Option(
      "\tThe maximum number of grid extensions (-1 is unlimited).\n"
        + "\t(default: 3)", "max-grid-extensions", 1,
      "-max-grid-extensions <num>"));

    result.addElement(new Option(
      "\tThe size (in percent) of the sample to search the inital grid with.\n"
        + "\t(default: 100)", "sample-size", 1, "-sample-size <num>"));

    result.addElement(new Option("\tThe type of traversal for the grid.\n"
      + "\t(default: " + new SelectedTag(TRAVERSAL_BY_COLUMN, TAGS_TRAVERSAL)
      + ")", "traversal", 1, "-traversal " + Tag.toOptionList(TAGS_TRAVERSAL)));

    result.addElement(new Option("\tThe log file to log the messages to.\n"
      + "\t(default: none)", "log-file", 1, "-log-file <filename>"));

    result.addElement(new Option("\tNumber of execution slots.\n"
      + "\t(default 1 - i.e. no parallelism)", "num-slots", 1,
      "-num-slots <num>"));

    result.addAll(Collections.list(super.listOptions()));

    return result.elements();
  }

  /**
   * returns the options of the current setup.
   * 
   * @return the current options
   */
  @Override
  public String[] getOptions() {

    Vector<String> result = new Vector<String>();

    result.add("-E");
    result.add("" + getEvaluation());

    result.add("-y-property");
    result.add("" + getYProperty());

    result.add("-y-min");
    result.add("" + getYMin());

    result.add("-y-max");
    result.add("" + getYMax());

    result.add("-y-step");
    result.add("" + getYStep());

    result.add("-y-base");
    result.add("" + getYBase());

    result.add("-y-expression");
    result.add("" + getYExpression());

    result.add("-x-property");
    result.add("" + getXProperty());

    result.add("-x-min");
    result.add("" + getXMin());

    result.add("-x-max");
    result.add("" + getXMax());

    result.add("-x-step");
    result.add("" + getXStep());

    result.add("-x-base");
    result.add("" + getXBase());

    result.add("-x-expression");
    result.add("" + getXExpression());

    if (getGridIsExtendable()) {
      result.add("-extend-grid");
      result.add("-max-grid-extensions");
      result.add("" + getMaxGridExtensions());
    }

    result.add("-sample-size");
    result.add("" + getSampleSizePercent());

    result.add("-traversal");
    result.add("" + getTraversal());

    result.add("-log-file");
    result.add("" + getLogFile());

    result.add("-num-slots");
    result.add("" + getNumExecutionSlots());

    Collections.addAll(result, super.getOptions());

    return result.toArray(new String[result.size()]);
  }

  /**
   * Parses the options for this object.
   * <p/>
   * 
   * <!-- options-start --> Valid options are:
   * <p/>
   * 
   * <pre>
   * -E &lt;CC|RMSE|RRSE|MAE|RAE|COMB|ACC|WAUC|KAP&gt;
   *  Determines the parameter used for evaluation:
   *  CC = Correlation coefficient
   *  RMSE = Root mean squared error
   *  RRSE = Root relative squared error
   *  MAE = Mean absolute error
   *  RAE = Root absolute error
   *  COMB = Combined = (1-abs(CC)) + RRSE + RAE
   *  ACC = Accuracy
   *  WAUC = Weighted AUC
   *  KAP = Kappa
   *  (default: CC)
   * </pre>
   * 
   * <pre>
   * -y-property &lt;option&gt;
   *  The Y option to test (without leading dash).
   *  (default: kernel.gamma)
   * </pre>
   * 
   * <pre>
   * -y-min &lt;num&gt;
   *  The minimum for Y.
   *  (default: -3)
   * </pre>
   * 
   * <pre>
   * -y-max &lt;num&gt;
   *  The maximum for Y.
   *  (default: +3)
   * </pre>
   * 
   * <pre>
   * -y-step &lt;num&gt;
   *  The step size for Y.
   *  (default: 1)
   * </pre>
   * 
   * <pre>
   * -y-base &lt;num&gt;
   *  The base for Y.
   *  (default: 10)
   * </pre>
   * 
   * <pre>
   * -y-expression &lt;expr&gt;
   *  The expression for Y.
   *  Available parameters:
   *   BASE
   *   FROM
   *   TO
   *   STEP
   *   I - the current iteration value
   *   (from 'FROM' to 'TO' with stepsize 'STEP')
   *  (default: 'pow(BASE,I)')
   * </pre>
   * 
   * <pre>
   * -x-property &lt;option&gt;
   *  The X option to test (without leading dash).
   *  (default: C)
   * </pre>
   * 
   * <pre>
   * -x-min &lt;num&gt;
   *  The minimum for X.
   *  (default: -3)
   * </pre>
   * 
   * <pre>
   * -x-max &lt;num&gt;
   *  The maximum for X.
   *  (default: 3)
   * </pre>
   * 
   * <pre>
   * -x-step &lt;num&gt;
   *  The step size for X.
   *  (default: 1)
   * </pre>
   * 
   * <pre>
   * -x-base &lt;num&gt;
   *  The base for X.
   *  (default: 10)
   * </pre>
   * 
   * <pre>
   * -x-expression &lt;expr&gt;
   *  The expression for the X value.
   *  Available parameters:
   *   BASE
   *   MIN
   *   MAX
   *   STEP
   *   I - the current iteration value
   *   (from 'FROM' to 'TO' with stepsize 'STEP')
   *  (default: 'pow(BASE,I)')
   * </pre>
   * 
   * <pre>
   * -extend-grid
   *  Whether the grid can be extended.
   *  (default: no)
   * </pre>
   * 
   * <pre>
   * -max-grid-extensions &lt;num&gt;
   *  The maximum number of grid extensions (-1 is unlimited).
   *  (default: 3)
   * </pre>
   * 
   * <pre>
   * -sample-size &lt;num&gt;
   *  The size (in percent) of the sample to search the inital grid with.
   *  (default: 100)
   * </pre>
   * 
   * <pre>
   * -traversal &lt;ROW-WISE|COLUMN-WISE&gt;
   *  The type of traversal for the grid.
   *  (default: COLUMN-WISE)
   * </pre>
   * 
   * <pre>
   * -log-file &lt;filename&gt;
   *  The log file to log the messages to.
   *  (default: none)
   * </pre>
   * 
   * <pre>
   * -num-slots &lt;num&gt;
   *  Number of execution slots.
   *  (default 1 - i.e. no parallelism)
   * </pre>
   * 
   * <pre>
   * -S &lt;num&gt;
   *  Random number seed.
   *  (default 1)
   * </pre>
   * 
   * <pre>
   * -W
   *  Full name of base classifier.
   *  (default: weka.classifiers.functions.SMOreg with options -K weka.classifiers.functions.supportVector.RBFKernel)
   * </pre>
   * 
   * <pre>
   * -output-debug-info
   *  If set, classifier is run in debug mode and
   *  may output additional info to the console
   * </pre>
   * 
   * <pre>
   * -do-not-check-capabilities
   *  If set, classifier capabilities are not checked before classifier is built
   *  (use with caution).
   * </pre>
   * 
   * <pre>
   * Options specific to classifier weka.classifiers.functions.SMOreg:
   * </pre>
   * 
   * <pre>
   * -C &lt;double&gt;
   *  The complexity constant C.
   *  (default 1)
   * </pre>
   * 
   * <pre>
   * -N
   *  Whether to 0=normalize/1=standardize/2=neither.
   *  (default 0=normalize)
   * </pre>
   * 
   * <pre>
   * -I &lt;classname and parameters&gt;
   *  Optimizer class used for solving quadratic optimization problem
   *  (default weka.classifiers.functions.supportVector.RegSMOImproved)
   * </pre>
   * 
   * <pre>
   * -K &lt;classname and parameters&gt;
   *  The Kernel to use.
   *  (default: weka.classifiers.functions.supportVector.PolyKernel)
   * </pre>
   * 
   * <pre>
   * -output-debug-info
   *  If set, classifier is run in debug mode and
   *  may output additional info to the console
   * </pre>
   * 
   * <pre>
   * -do-not-check-capabilities
   *  If set, classifier capabilities are not checked before classifier is built
   *  (use with caution).
   * </pre>
   * 
   * <pre>
   * Options specific to optimizer ('-I') weka.classifiers.functions.supportVector.RegSMOImproved:
   * </pre>
   * 
   * <pre>
   * -T &lt;double&gt;
   *  The tolerance parameter for checking the stopping criterion.
   *  (default 0.001)
   * </pre>
   * 
   * <pre>
   * -V
   *  Use variant 1 of the algorithm when true, otherwise use variant 2.
   *  (default true)
   * </pre>
   * 
   * <pre>
   * -P &lt;double&gt;
   *  The epsilon for round-off error.
   *  (default 1.0e-12)
   * </pre>
   * 
   * <pre>
   * -L &lt;double&gt;
   *  The epsilon parameter in epsilon-insensitive loss function.
   *  (default 1.0e-3)
   * </pre>
   * 
   * <pre>
   * -W &lt;double&gt;
   *  The random number seed.
   *  (default 1)
   * </pre>
   * 
   * <pre>
   * Options specific to kernel ('-K') weka.classifiers.functions.supportVector.RBFKernel:
   * </pre>
   * 
   * <pre>
   * -G &lt;num&gt;
   *  The Gamma parameter.
   *  (default: 0.01)
   * </pre>
   * 
   * <pre>
   * -C &lt;num&gt;
   *  The size of the cache (a prime number), 0 for full cache and 
   *  -1 to turn it off.
   *  (default: 250007)
   * </pre>
   * 
   * <pre>
   * -output-debug-info
   *  Enables debugging output (if available) to be printed.
   *  (default: off)
   * </pre>
   * 
   * <pre>
   * -no-checks
   *  Turns off all checks - use with caution!
   *  (default: checks on)
   * </pre>
   * 
   * <!-- options-end -->
   * 
   * @param options the options to use
   * @throws Exception if setting of options fails
   */
  @Override
  public void setOptions(String[] options) throws Exception {
    String tmpStr;

    tmpStr = Utils.getOption('E', options);
    if (tmpStr.length() != 0) {
      setEvaluation(new SelectedTag(tmpStr, TAGS_EVALUATION));
    } else {
      setEvaluation(new SelectedTag(EVALUATION_CC, TAGS_EVALUATION));
    }

    tmpStr = Utils.getOption("y-property", options);
    if (tmpStr.length() != 0) {
      setYProperty(tmpStr);
    } else {
      setYProperty("kernel.gamma");
    }

    tmpStr = Utils.getOption("y-min", options);
    if (tmpStr.length() != 0) {
      setYMin(Double.parseDouble(tmpStr));
    } else {
      setYMin(-3);
    }

    tmpStr = Utils.getOption("y-max", options);
    if (tmpStr.length() != 0) {
      setYMax(Double.parseDouble(tmpStr));
    } else {
      setYMax(3);
    }

    tmpStr = Utils.getOption("y-step", options);
    if (tmpStr.length() != 0) {
      setYStep(Double.parseDouble(tmpStr));
    } else {
      setYStep(1);
    }

    tmpStr = Utils.getOption("y-base", options);
    if (tmpStr.length() != 0) {
      setYBase(Double.parseDouble(tmpStr));
    } else {
      setYBase(10);
    }

    tmpStr = Utils.getOption("y-expression", options);
    if (tmpStr.length() != 0) {
      setYExpression(tmpStr);
    } else {
      setYExpression("pow(BASE,I)");
    }

    tmpStr = Utils.getOption("x-property", options);
    if (tmpStr.length() != 0) {
      setXProperty(tmpStr);
    } else {
      setXProperty("C");
    }

    tmpStr = Utils.getOption("x-min", options);
    if (tmpStr.length() != 0) {
      setXMin(Double.parseDouble(tmpStr));
    } else {
      setXMin(-3);
    }

    tmpStr = Utils.getOption("x-max", options);
    if (tmpStr.length() != 0) {
      setXMax(Double.parseDouble(tmpStr));
    } else {
      setXMax(3);
    }

    tmpStr = Utils.getOption("x-step", options);
    if (tmpStr.length() != 0) {
      setXStep(Double.parseDouble(tmpStr));
    } else {
      setXStep(1);
    }

    tmpStr = Utils.getOption("x-base", options);
    if (tmpStr.length() != 0) {
      setXBase(Double.parseDouble(tmpStr));
    } else {
      setXBase(10);
    }

    tmpStr = Utils.getOption("x-expression", options);
    if (tmpStr.length() != 0) {
      setXExpression(tmpStr);
    } else {
      setXExpression("pow(BASE,I)");
    }

    setGridIsExtendable(Utils.getFlag("extend-grid", options));
    if (getGridIsExtendable()) {
      tmpStr = Utils.getOption("max-grid-extensions", options);
      if (tmpStr.length() != 0) {
        setMaxGridExtensions(Integer.parseInt(tmpStr));
      } else {
        setMaxGridExtensions(3);
      }
    }

    tmpStr = Utils.getOption("sample-size", options);
    if (tmpStr.length() != 0) {
      setSampleSizePercent(Double.parseDouble(tmpStr));
    } else {
      setSampleSizePercent(100);
    }

    tmpStr = Utils.getOption("traversal", options);
    if (tmpStr.length() != 0) {
      setTraversal(new SelectedTag(tmpStr, TAGS_TRAVERSAL));
    } else {
      setTraversal(new SelectedTag(TRAVERSAL_BY_ROW, TAGS_TRAVERSAL));
    }

    tmpStr = Utils.getOption("log-file", options);
    if (tmpStr.length() != 0) {
      setLogFile(new File(tmpStr));
    } else {
      setLogFile(new File(System.getProperty("user.dir")));
    }

    tmpStr = Utils.getOption("num-slots", options);
    if (tmpStr.length() != 0) {
      setNumExecutionSlots(Integer.parseInt(tmpStr));
    } else {
      setNumExecutionSlots(1);
    }

    super.setOptions(options);

    Utils.checkForRemainingOptions(options);
  }

  /**
   * Set the base learner.
   * 
   * @param newClassifier the classifier to use.
   */
  @Override
  public void setClassifier(Classifier newClassifier) {
    boolean numeric;
    boolean nominal;

    Capabilities cap = newClassifier.getCapabilities();

    numeric = cap.handles(Capability.NUMERIC_CLASS)
      || cap.hasDependency(Capability.NUMERIC_CLASS);

    nominal = cap.handles(Capability.NOMINAL_CLASS)
      || cap.hasDependency(Capability.NOMINAL_CLASS)
      || cap.handles(Capability.BINARY_CLASS)
      || cap.hasDependency(Capability.BINARY_CLASS)
      || cap.handles(Capability.UNARY_CLASS)
      || cap.hasDependency(Capability.UNARY_CLASS);

    if ((m_Evaluation == EVALUATION_CC) && !numeric) {
      System.err.println("WARNING: Classifier needs to handle numeric class for chosen type of evaluation!");
    }

    if (((m_Evaluation == EVALUATION_ACC) || (m_Evaluation == EVALUATION_KAPPA) || (m_Evaluation == EVALUATION_WAUC))
      && !nominal) {
      System.err.println("Classifier needs to handle nominal class for chosen type of evaluation!");
    }

    super.setClassifier(newClassifier);

    try {
      m_BestClassifier = AbstractClassifier.makeCopy(m_Classifier);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String evaluationTipText() {
    return "Sets the criterion for evaluating the classifier performance and "
      + "choosing the best one.";
  }

  /**
   * Sets the criterion to use for evaluating the classifier performance.
   * 
   * @param value the evaluation criterion
   */
  public void setEvaluation(SelectedTag value) {
    if (value.getTags() == TAGS_EVALUATION) {
      m_Evaluation = value.getSelectedTag().getID();
    }
  }

  /**
   * Gets the criterion used for evaluating the classifier performance.
   * 
   * @return the current evaluation criterion.
   */
  public SelectedTag getEvaluation() {
    return new SelectedTag(m_Evaluation, TAGS_EVALUATION);
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String YPropertyTipText() {
    return "The Y property to test (normally the classifier).";
  }

  /**
   * Get the Y property (normally the classifier).
   * 
   * @return Value of the property.
   */
  public String getYProperty() {
    return m_Y_Property;
  }

  /**
   * Set the Y property (normally the classifier).
   * 
   * @param value the Y property.
   */
  public void setYProperty(String value) {
    m_Y_Property = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String YMinTipText() {
    return "The minimum of Y (normally the classifier).";
  }

  /**
   * Get the value of the minimum of Y.
   * 
   * @return Value of the minimum of Y.
   */
  public double getYMin() {
    return m_Y_Min;
  }

  /**
   * Set the value of the minimum of Y.
   * 
   * @param value Value to use as minimum of Y.
   */
  public void setYMin(double value) {
    m_Y_Min = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String YMaxTipText() {
    return "The maximum of Y.";
  }

  /**
   * Get the value of the Maximum of Y.
   * 
   * @return Value of the Maximum of Y.
   */
  public double getYMax() {
    return m_Y_Max;
  }

  /**
   * Set the value of the Maximum of Y.
   * 
   * @param value Value to use as Maximum of Y.
   */
  public void setYMax(double value) {
    m_Y_Max = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String YStepTipText() {
    return "The step size of Y.";
  }

  /**
   * Get the value of the step size for Y.
   * 
   * @return Value of the step size for Y.
   */
  public double getYStep() {
    return m_Y_Step;
  }

  /**
   * Set the value of the step size for Y.
   * 
   * @param value Value to use as the step size for Y.
   */
  public void setYStep(double value) {
    m_Y_Step = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String YBaseTipText() {
    return "The base of Y.";
  }

  /**
   * Get the value of the base for Y.
   * 
   * @return Value of the base for Y.
   */
  public double getYBase() {
    return m_Y_Base;
  }

  /**
   * Set the value of the base for Y.
   * 
   * @param value Value to use as the base for Y.
   */
  public void setYBase(double value) {
    m_Y_Base = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String YExpressionTipText() {
    return "The expression for the Y value (parameters: BASE, FROM, TO, STEP, I).";
  }

  /**
   * Get the expression for the Y value.
   * 
   * @return Expression for the Y value.
   */
  public String getYExpression() {
    return m_Y_Expression;
  }

  /**
   * Set the expression for the Y value.
   * 
   * @param value Expression for the Y value.
   */
  public void setYExpression(String value) {
    m_Y_Expression = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String XPropertyTipText() {
    return "The X property to test.";
  }

  /**
   * Get the X property to test.
   * 
   * @return Value of the X property.
   */
  public String getXProperty() {
    return m_X_Property;
  }

  /**
   * Set the X property.
   * 
   * @param value the X property.
   */
  public void setXProperty(String value) {
    m_X_Property = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String XMinTipText() {
    return "The minimum of X.";
  }

  /**
   * Get the value of the minimum of X.
   * 
   * @return Value of the minimum of X.
   */
  public double getXMin() {
    return m_X_Min;
  }

  /**
   * Set the value of the minimum of X.
   * 
   * @param value Value to use as minimum of X.
   */
  public void setXMin(double value) {
    m_X_Min = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String XMaxTipText() {
    return "The maximum of X.";
  }

  /**
   * Get the value of the Maximum of X.
   * 
   * @return Value of the Maximum of X.
   */
  public double getXMax() {
    return m_X_Max;
  }

  /**
   * Set the value of the Maximum of X.
   * 
   * @param value Value to use as Maximum of X.
   */
  public void setXMax(double value) {
    m_X_Max = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String XStepTipText() {
    return "The step size of X.";
  }

  /**
   * Get the value of the step size for X.
   * 
   * @return Value of the step size for X.
   */
  public double getXStep() {
    return m_X_Step;
  }

  /**
   * Set the value of the step size for X.
   * 
   * @param value Value to use as the step size for X.
   */
  public void setXStep(double value) {
    m_X_Step = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String XBaseTipText() {
    return "The base of X.";
  }

  /**
   * Get the value of the base for X.
   * 
   * @return Value of the base for X.
   */
  public double getXBase() {
    return m_X_Base;
  }

  /**
   * Set the value of the base for X.
   * 
   * @param value Value to use as the base for X.
   */
  public void setXBase(double value) {
    m_X_Base = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String XExpressionTipText() {
    return "The expression for the X value (parameters: BASE, FROM, TO, STEP, I).";
  }

  /**
   * Get the expression for the X value.
   * 
   * @return Expression for the X value.
   */
  public String getXExpression() {
    return m_X_Expression;
  }

  /**
   * Set the expression for the X value.
   * 
   * @param value Expression for the X value.
   */
  public void setXExpression(String value) {
    m_X_Expression = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String gridIsExtendableTipText() {
    return "Whether the grid can be extended.";
  }

  /**
   * Get whether the grid can be extended dynamically.
   * 
   * @return true if the grid can be extended.
   */
  public boolean getGridIsExtendable() {
    return m_GridIsExtendable;
  }

  /**
   * Set whether the grid can be extended dynamically.
   * 
   * @param value whether the grid can be extended dynamically.
   */
  public void setGridIsExtendable(boolean value) {
    m_GridIsExtendable = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String maxGridExtensionsTipText() {
    return "The maximum number of grid extensions, -1 for unlimited.";
  }

  /**
   * Gets the maximum number of grid extensions, -1 for unlimited.
   * 
   * @return the max number of grid extensions
   */
  public int getMaxGridExtensions() {
    return m_MaxGridExtensions;
  }

  /**
   * Sets the maximum number of grid extensions, -1 for unlimited.
   * 
   * @param value the maximum of grid extensions.
   */
  public void setMaxGridExtensions(int value) {
    m_MaxGridExtensions = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String sampleSizePercentTipText() {
    return "The sample size (in percent) to use in the initial grid search.";
  }

  /**
   * Gets the sample size for the initial grid search.
   * 
   * @return the sample size.
   */
  public double getSampleSizePercent() {
    return m_SampleSize;
  }

  /**
   * Sets the sample size for the initial grid search.
   * 
   * @param value the sample size for the initial grid search.
   */
  public void setSampleSizePercent(double value) {
    m_SampleSize = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String traversalTipText() {
    return "Sets type of traversal of the grid, either by rows or columns.";
  }

  /**
   * Sets the type of traversal for the grid.
   * 
   * @param value the traversal type
   */
  public void setTraversal(SelectedTag value) {
    if (value.getTags() == TAGS_TRAVERSAL) {
      m_Traversal = value.getSelectedTag().getID();
    }
  }

  /**
   * Gets the type of traversal for the grid.
   * 
   * @return the current traversal type.
   */
  public SelectedTag getTraversal() {
    return new SelectedTag(m_Traversal, TAGS_TRAVERSAL);
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String logFileTipText() {
    return "The log file to log the messages to.";
  }

  /**
   * Gets current log file.
   * 
   * @return the log file.
   */
  public File getLogFile() {
    return m_LogFile;
  }

  /**
   * Sets the log file to use.
   * 
   * @param value the log file.
   */
  public void setLogFile(File value) {
    m_LogFile = value;
  }

  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String numExecutionSlotsTipText() {
    return "The number of execution slots (threads) to use for "
      + "finding optimal parameters.";
  }

  /**
   * Set the number of execution slots (threads) to use for finding optimal parameters.
   * 
   * @param value the number of slots to use.
   */
  public void setNumExecutionSlots(int value) {
    if (value >= 1) {
      m_NumExecutionSlots = value;
    }
  }

  /**
   * Get the number of execution slots (threads) to use for finding optimal parameters.
   * 
   * @return the number of slots to use
   */
  public int getNumExecutionSlots() {
    return m_NumExecutionSlots;
  }

  /**
   * Adds the performance to the cache and the current list of performances.
   * Does nothing if at least one setup failed.
   * 
   * @param performance the performance to add
   * @param folds the number of folds
   * @see #m_Failed
   */
  protected void addPerformance(Performance performance, int folds) {

    m_Performances.add(performance);
    m_Cache.add(folds, performance);
  }

  /**
   * Returns the data currently in use.
   * 
   * @return the data
   */
  protected Instances getData() {
    return m_Data;
  }

  /**
   * returns the best Classifier setup.
   * 
   * @return the best Classifier setup
   */
  public Classifier getBestClassifier() {
    return m_BestClassifier;
  }

  /**
   * Returns an enumeration of the measure names.
   * 
   * @return an enumeration of the measure names
   */
  @Override
  public Enumeration<String> enumerateMeasures() {

    Vector<String> result = new Vector<String>();

    result.add("measureX");
    result.add("measureY");
    result.add("measureGridExtensionsPerformed");

    return result.elements();
  }

  /**
   * Returns the value of the named measure.
   * 
   * @param measureName the name of the measure to query for its value
   * @return the value of the named measure
   */
  @Override
  public double getMeasure(String measureName) {
    if (measureName.equalsIgnoreCase("measureX")) {
      return m_Generator.evaluate(getValues().getX(), true);
    } else if (measureName.equalsIgnoreCase("measureY")) {
      return m_Generator.evaluate(getValues().getY(), false);
    } else if (measureName.equalsIgnoreCase("measureGridExtensionsPerformed")) {
      return getGridExtensionsPerformed();
    } else {
      throw new IllegalArgumentException("Measure '" + measureName
        + "' not supported!");
    }
  }

  /**
   * returns the parameter pair that was found to work best.
   * 
   * @return the best parameter combination
   */
  public PointDouble getValues() {
    return m_Values;
  }

  /**
   * returns the number of grid extensions that took place during the search
   * (only applicable if the grid was extendable).
   * 
   * @return the number of grid extensions that were performed
   * @see #getGridIsExtendable()
   */
  public int getGridExtensionsPerformed() {
    return m_GridExtensionsPerformed;
  }

  /**
   * Returns default capabilities of the classifier.
   * 
   * @return the capabilities of this classifier
   */
  @Override
  public Capabilities getCapabilities() {
    Capabilities result;
    Capabilities classes;
    Iterator<Capability> iter;
    Capability capab;

    result = super.getCapabilities();

    // only nominal and numeric classes allowed
    classes = result.getClassCapabilities();
    iter = classes.capabilities();
    while (iter.hasNext()) {
      capab = iter.next();
      if ((capab != Capability.BINARY_CLASS)
        && (capab != Capability.UNARY_CLASS)
        && (capab != Capability.NOMINAL_CLASS)
        && (capab != Capability.NUMERIC_CLASS)
        && (capab != Capability.DATE_CLASS)) {
        result.disable(capab);
      }
    }

    result.enable(Capability.MISSING_CLASS_VALUES);

    // set dependencies
    for (Capability cap : Capability.values()) {
      result.enableDependency(cap);
    }

    if (result.getMinimumNumberInstances() < 1) {
      result.setMinimumNumberInstances(1);
    }

    result.setOwner(this);

    return result;
  }

  /**
   * prints the specified message to stdout if debug is on and can also dump the
   * message to a log file.
   * 
   * @param message the message to print or store in a log file
   */
  protected void log(String message) {
    log(message, false);
  }

  /**
   * prints the specified message to stdout if debug is on and can also dump the
   * message to a log file.
   * 
   * @param message the message to print or store in a log file
   * @param onlyLog if true the message will only be put into the log file but
   *          not to stdout
   */
  protected void log(String message, boolean onlyLog) {
    // print to stdout?
    if (getDebug() && (!onlyLog)) {
      System.out.println(message);
    }

    // log file?
    if (!getLogFile().isDirectory()) {
      Debug.writeToFile(getLogFile().getAbsolutePath(), message, true);
    }
  }

  /**
   * generates a table string for all the performances in the grid and returns
   * that.
   * 
   * @param grid the current grid to align the performances to
   * @param performances the performances to align
   * @param type the type of performance
   * @return the table string
   */
  protected String logPerformances(Grid grid, Vector<Performance> performances,
    Tag type) {
    StringBuffer result;
    PerformanceTable table;

    result = new StringBuffer(type.getReadable() + ":\n");
    table = new PerformanceTable(this, grid, performances, type.getID());

    result.append(table.toString() + "\n");
    result.append("\n");
    result.append(table.toGnuplot() + "\n");
    result.append("\n");

    return result.toString();
  }

  /**
   * aligns all performances in the grid and prints those tables to the log
   * file.
   * 
   * @param grid the current grid to align the performances to
   * @param performances the performances to align
   */
  protected void logPerformances(Grid grid, Vector<Performance> performances) {
    int i;

    for (i = 0; i < TAGS_EVALUATION.length; i++) {
      log("\n" + logPerformances(grid, performances, TAGS_EVALUATION[i]), true);
    }
  }

  /**
   * Start the pool of execution threads.
   */
  protected void startExecutorPool() {
    stopExecutorPool();

    log("Starting thread pool with " + m_NumExecutionSlots + " slots...");

    m_ExecutorPool = Executors.newFixedThreadPool(m_NumExecutionSlots);

  }

  /**
   * Stops the ppol of execution threads.
   */
  protected void stopExecutorPool() {
    log("Shutting down thread pool...");

    if (m_ExecutorPool != null) {
      m_ExecutorPool.shutdownNow();
    }

    m_ExecutorPool = null;
  }

  /**
   * Records the completion of the training of a single classifier. Unblocks if
   * all classifiers have been trained.
   * 
   * @param obj the classifier or setup values that was attempted to train
   * @param exception an optional exception. evaluation was successful if this
   *          is null
   */
  protected synchronized void completedEvaluation(Object obj,
    Exception exception) {
    if (exception != null) {
      m_Failed++;
      if (m_Debug) {
        System.err.println("Training failed: " + Utils.toCommandLine(obj));
      }
    } else {
      m_Completed++;
    }

    if (m_Debug) {
      System.err.println("Progress: completed=" + m_Completed + ", failed="
        + m_Failed + ", overall=" + m_NumSetups);
    }

    m_Exception = exception;
  }

  /**
   * determines the best values-pair for the given grid, using CV with specified
   * number of folds.
   * 
   * @param grid the grid to work on
   * @param inst the data to work with
   * @param cv the number of folds for the cross-validation
   * @return the best values pair
   * @throws Exception if setup or training fails
   */
  protected PointDouble determineBestInGrid(Grid grid, Instances inst, int cv)
    throws Exception {
    int i;
    Enumeration<PointDouble> enm;
    PointDouble values;
    PointDouble result;
    int size;
    boolean allCached;
    Performance p1;
    Performance p2;
    EvaluationTask newTask;

    startExecutorPool();
    m_Performances.clear();

    log("Determining best pair with " + cv + "-fold CV in Grid:\n" + grid
      + "\n");

    if (m_Traversal == TRAVERSAL_BY_COLUMN) {
      size = grid.width();
    } else {
      size = grid.height();
    }

    allCached = true;
    m_Failed = 0;
    m_Completed = 0;
    m_NumSetups = grid.width() * grid.height();

    ArrayList<Future<Exception>> results = new ArrayList<Future<Exception>>();
    for (i = 0; i < size; i++) {
      if (m_Traversal == TRAVERSAL_BY_COLUMN) {
        enm = grid.column(i);
      } else {
        enm = grid.row(i);
      }

      while (enm.hasMoreElements()) {
        values = enm.nextElement();

        // already calculated?
        if (m_Cache.isCached(cv, values)) {
          m_Performances.add(m_Cache.get(cv, values));
          m_Completed++;
          log("" + m_Performances.get(m_Performances.size() - 1)
            + ": cached=true");
        } else {
          allCached = false;
          newTask = new EvaluationTask(this, m_Generator, inst, values, cv,
            m_Evaluation);
          results.add(m_ExecutorPool.submit(newTask));
        }
      }
    }

    // wait for execution to finish
    try {
      for (Future<Exception> future : results) {
        if (future.get() != null) {
          throw new IllegalStateException(future.get().getMessage());
        }
      }
    } catch (Exception e) {
      stopExecutorPool();
      throw new IllegalStateException("Thread-based execution of evaluation tasks failed: " +
              e.getMessage());
    }

    stopExecutorPool();

    if (allCached) {
      log("All points were already cached - abnormal state!");
      throw new IllegalStateException(
        "All points were already cached - abnormal state!");
    }

    if (m_Failed > 0) {
      if (m_Exception != null) {
        throw m_Exception;
      } else {
        throw new WekaException("Searched stopped due to failed setup!");
      }
    }

    // sort list
    Collections.sort(m_Performances, new PerformanceComparator(m_Evaluation));

    result = m_Performances.get(m_Performances.size() - 1).getValues();

    // check whether all performances are the same
    m_UniformPerformance = true;
    p1 = m_Performances.get(0);
    for (i = 1; i < m_Performances.size(); i++) {
      p2 = m_Performances.get(i);
      if (p2.getPerformance(m_Evaluation) != p1.getPerformance(m_Evaluation)) {
        m_UniformPerformance = false;
        break;
      }
    }
    if (m_UniformPerformance) {
      log("All performances are the same!");
    }

    logPerformances(grid, m_Performances);
    log("\nBest performance:\n" + m_Performances.get(m_Performances.size() - 1));

    m_Performances.clear();

    return result;
  }

  /**
   * returns the best values-pair in the grid.
   * 
   * @return the best values pair
   * @throws Exception if something goes wrong
   */
  protected PointDouble findBest() throws Exception {
    PointInt center;
    Grid neighborGrid;
    boolean finished;
    PointDouble result;
    PointDouble resultOld;
    int iteration;
    Instances sample;
    Resample resample;

    log("Step 1:\n");

    // generate sample?
    if (getSampleSizePercent() == 100) {
      sample = m_Data;
    } else {
      log("Generating sample (" + getSampleSizePercent() + "%)");
      resample = new Resample();
      resample.setRandomSeed(getSeed());
      resample.setSampleSizePercent(getSampleSizePercent());
      resample.setInputFormat(m_Data);
      sample = Filter.useFilter(m_Data, resample);
    }

    finished = false;
    iteration = 0;
    m_GridExtensionsPerformed = 0;
    m_UniformPerformance = false;

    // find first center
    log("\n=== Initial grid - Start ===");
    result = determineBestInGrid(m_Grid, sample, 2);
    log("\nResult of Step 1: " + result + "\n");
    log("=== Initial grid - End ===\n");

    finished = m_UniformPerformance;

    if (!finished) {
      do {
        iteration++;
        resultOld = (PointDouble) result.clone();
        center = m_Grid.getLocation(result);
        // on border? -> finished (if it cannot be extended)
        if (m_Grid.isOnBorder(center)) {
          log("Center is on border of grid.");

          // can we extend grid?
          if (getGridIsExtendable()) {
            // max number of extensions reached?
            if (m_GridExtensionsPerformed == getMaxGridExtensions()) {
              log("Maximum number of extensions reached!\n");
              finished = true;
            } else {
              m_GridExtensionsPerformed++;
              m_Grid = m_Grid.extend(result);
              center = m_Grid.getLocation(result);
              log("Extending grid (" + m_GridExtensionsPerformed + "/"
                + getMaxGridExtensions() + "):\n" + m_Grid + "\n");
            }
          } else {
            finished = true;
          }
        }

        // new grid with current best one at center and immediate neighbors
        // around it
        if (!finished) {
          neighborGrid = m_Grid.subgrid((int) center.getY() + 1,
            (int) center.getX() - 1, (int) center.getY() - 1,
            (int) center.getX() + 1);
          result = determineBestInGrid(neighborGrid, sample, 10);
          log("\nResult of Step 2/Iteration " + (iteration) + ":\n" + result);
          finished = m_UniformPerformance;

          // no improvement?
          if (result.equals(resultOld)) {
            finished = true;
            log("\nNo better point found.");
          }
        }
      } while (!finished);
    }

    log("\nFinal result: " + result);

    return result;
  }

  /**
   * builds the classifier.
   * 
   * @param data the training instances
   * @throws Exception if something goes wrong
   */
  @Override
  public void buildClassifier(Instances data) throws Exception {
    String strX;
    String strY;
    double x;
    double y;

    // can classifier handle the data?
    getCapabilities().testWithFail(data);

    // remove instances with missing class
    m_Data = new Instances(data);
    m_Data.deleteWithMissingClass();

    m_Cache = new PerformanceCache();
    m_Performances = new Vector<Performance>();
    m_Generator = new SetupGenerator(this);
    m_Exception = null;

    strX = m_Classifier.getClass().getName();
    strY = m_Classifier.getClass().getName();

    m_Grid = new Grid(getXMin(), getXMax(), getXStep(),
      strX + ", property " + getXProperty() + ", expr. " + getXExpression()
        + ", base " + getXBase(), getYMin(), getYMax(), getYStep(), strY
        + ", property " + getYProperty() + ", expr. " + getYExpression()
        + ", base " + getYBase());

    log("\n" + this.getClass().getName() + "\n"
      + this.getClass().getName().replaceAll(".", "=") + "\n" + "Options: "
      + Utils.joinOptions(getOptions()) + "\n");

    // find best
    m_Values = findBest();

    // setup best configurations
    x = m_Generator.evaluate(m_Values.getX(), true);
    y = m_Generator.evaluate(m_Values.getY(), false);
    m_BestClassifier = (Classifier) m_Generator.setup(getClassifier(), x, y);

    // train classifier
    m_Classifier = (Classifier) m_Generator.setup(getClassifier(), x, y);
    m_Classifier.buildClassifier(m_Data);

    // Don't need data anymore in this class
    m_Data = null;
  }

  /**
   * Computes the distribution for a given instance
   * 
   * @param instance the instance for which distribution is computed
   * @return the distribution
   * @throws Exception if the distribution can't be computed successfully
   */
  @Override
  public double[] distributionForInstance(Instance instance) throws Exception {

    return m_Classifier.distributionForInstance(instance);
  }

  /**
   * returns a string representation of the classifier.
   * 
   * @return a string representation of the classifier
   */
  @Override
  public String toString() {
    String result;

    result = "";

    if (m_Values == null) {
      result = "No search performed yet.";
    } else {
      result = this.getClass().getName() + ":\n" + "Classifier: "
        + Utils.toCommandLine(getClassifier()) + "\n\n" + "X property: "
        + getXProperty() + "\n" + "Y property: " + getYProperty() + "\n\n"
        + "Evaluation: " + getEvaluation().getSelectedTag().getReadable()
        + "\n" + "Coordinates: " + getValues() + "\n";

      if (getGridIsExtendable()) {
        result += "Grid-Extensions: " + getGridExtensionsPerformed() + "\n";
      }

      result += "Values: " + m_Generator.evaluate(getValues().getX(), true)
        + " (X coordinate)" + ", "
        + m_Generator.evaluate(getValues().getY(), false) + " (Y coordinate)"
        + "\n\n" + m_Classifier.toString();
    }

    return result;
  }

  /**
   * Returns a string that summarizes the object.
   * 
   * @return the object summarized as a string
   */
  @Override
  public String toSummaryString() {
    String result;

    result = "Best classifier: " + Utils.toCommandLine(getBestClassifier());

    return result;
  }

  /**
   * Returns the revision string.
   * 
   * @return the revision
   */
  @Override
  public String getRevision() {
    return RevisionUtils.extract("$Revision$");
  }

  /**
   * Main method for running this classifier from commandline.
   * 
   * @param args the options
   */
  public static void main(String[] args) {
    runClassifier(new GridSearch(), args);
  }
}
