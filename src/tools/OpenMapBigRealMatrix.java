/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tools;

import org.apache.commons.math3.linear.SparseRealMatrix;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.linear.*;


import java.io.Serializable;

/**
 * Sparse matrix implementation based on an open addressed map.
 *
 * <p>
 *  Caveat: This implementation assumes that, for any {@code x},
 *  the equality {@code x * 0d == 0d} holds. But it is is not true for
 *  {@code NaN}. Moreover, zero entries will lose their sign.
 *  Some operations (that involve {@code NaN} and/or infinities) may
 *  thus give incorrect results.
 * </p>
 * @since 2.0
 */
public class OpenMapBigRealMatrix extends AbstractRealMatrix
    implements SparseRealMatrix, Serializable {
    /** Serializable version identifier. */
    private static final long serialVersionUID = -5962461716457143437L;
    /** Number of rows of the matrix. */
    private final int rows;
    /** Number of columns of the matrix. */
    private final int columns;
    /** Storage for (sparse) matrix elements. */
    private final OpenLongToDoubleHashMap entries;

    /**
     * Build a sparse matrix with the supplied row and column dimensions.
     *
     * @param rowDimension Number of rows of the matrix.
     * @param columnDimension Number of columns of the matrix.
     * @throws org.apache.commons.math3.exception.NotStrictlyPositiveException if row or column dimension is not
     * positive.
     * @throws org.apache.commons.math3.exception.NumberIsTooLargeException if the total number of entries of the
     * matrix is larger than {@code Integer.MAX_VALUE}.
     */
    public OpenMapBigRealMatrix(int rowDimension, int columnDimension)
        throws NotStrictlyPositiveException, NumberIsTooLargeException {
        super(rowDimension, columnDimension);
        long lRow = rowDimension;
        long lCol = columnDimension;
        if (lRow * lCol >= Integer.MAX_VALUE) {
            // not doing this is the hope point
            //throw new NumberIsTooLargeException(lRow * lCol, Integer.MAX_VALUE, false);
        }
        this.rows = rowDimension;
        this.columns = columnDimension;
        this.entries = new OpenLongToDoubleHashMap(0.0);
    }

    /**
     * Build a matrix by copying another one.
     *
     * @param matrix matrix to copy.
     */
    public OpenMapBigRealMatrix(OpenMapBigRealMatrix matrix) {
        this.rows = matrix.rows;
        this.columns = matrix.columns;
        this.entries = new OpenLongToDoubleHashMap(matrix.entries);
    }

    /** {@inheritDoc} */
    @Override
    public OpenMapBigRealMatrix copy() {
        return new OpenMapBigRealMatrix(this);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NumberIsTooLargeException if the total number of entries of the
     * matrix is larger than {@code Integer.MAX_VALUE}.
     */
    @Override
    public OpenMapBigRealMatrix createMatrix(int rowDimension, int columnDimension)
        throws NotStrictlyPositiveException, NumberIsTooLargeException {
        return new OpenMapBigRealMatrix(rowDimension, columnDimension);
    }

    /** {@inheritDoc} */
    @Override
    public int getColumnDimension() {
        return columns;
    }

    /**
     * Compute the sum of this matrix and {@code m}.
     *
     * @param m Matrix to be added.
     * @return {@code this} + {@code m}.
     * @throws MatrixDimensionMismatchException if {@code m} is not the same
     * size as {@code this}.
     */
    public OpenMapBigRealMatrix add(OpenMapBigRealMatrix m)
        throws MatrixDimensionMismatchException {

        MatrixUtils.checkAdditionCompatible(this, m);

        final OpenMapBigRealMatrix out = new OpenMapBigRealMatrix(this);
        for (OpenLongToDoubleHashMap.Iterator iterator = m.entries.iterator(); iterator.hasNext();) {
            iterator.advance();
            final int row = (int) (iterator.key() / columns);
            final int col = (int) (iterator.key() - row * columns);
            out.setEntry(row, col, getEntry(row, col) + iterator.value());
        }

        return out;

    }

    /** {@inheritDoc} */
    @Override
    public OpenMapBigRealMatrix subtract(final RealMatrix m)
        throws MatrixDimensionMismatchException {
        try {
            return subtract((OpenMapBigRealMatrix) m);
        } catch (ClassCastException cce) {
            return (OpenMapBigRealMatrix) super.subtract(m);
        }
    }

    /**
     * Subtract {@code m} from this matrix.
     *
     * @param m Matrix to be subtracted.
     * @return {@code this} - {@code m}.
     * @throws MatrixDimensionMismatchException if {@code m} is not the same
     * size as {@code this}.
     */
    public OpenMapBigRealMatrix subtract(OpenMapBigRealMatrix m)
        throws MatrixDimensionMismatchException {
        MatrixUtils.checkAdditionCompatible(this, m);

        final OpenMapBigRealMatrix out = new OpenMapBigRealMatrix(this);
        for (OpenLongToDoubleHashMap.Iterator iterator = m.entries.iterator(); iterator.hasNext();) {
            iterator.advance();
            final int row = (int) (iterator.key() / columns);
            final int col = (int) (iterator.key() - row * columns);
            out.setEntry(row, col, getEntry(row, col) - iterator.value());
        }

        return out;
    }

    /**
     * {@inheritDoc}
     *
     * @throws NumberIsTooLargeException if {@code m} is an
     * {@code OpenMapRealMatrix}, and the total number of entries of the product
     * is larger than {@code Integer.MAX_VALUE}.
     */
    @Override
    public RealMatrix multiply(final RealMatrix m)
        throws DimensionMismatchException, NumberIsTooLargeException {
        try {
            return multiply((OpenMapBigRealMatrix) m);
        } catch (ClassCastException cce) {

            MatrixUtils.checkMultiplicationCompatible(this, m);

            final int outCols = m.getColumnDimension();
            final BlockRealMatrix out = new BlockRealMatrix(rows, outCols);
            for (OpenLongToDoubleHashMap.Iterator iterator = entries.iterator(); iterator.hasNext();) {
                iterator.advance();
                final double value = iterator.value();
                final long key      = iterator.key();
                final int i        = (int) (key / columns);
                final int k        = (int) (key % columns);
                for (int j = 0; j < outCols; ++j) {
                    out.addToEntry(i, j, value * m.getEntry(k, j));
                }
            }

            return out;
        }
    }

    /**
     * Postmultiply this matrix by {@code m}.
     *
     * @param m Matrix to postmultiply by.
     * @return {@code this} * {@code m}.
     * @throws DimensionMismatchException if the number of rows of {@code m}
     * differ from the number of columns of {@code this} matrix.
     * @throws NumberIsTooLargeException if the total number of entries of the
     * product is larger than {@code Integer.MAX_VALUE}.
     */
    public OpenMapBigRealMatrix multiply(OpenMapBigRealMatrix m)
        throws DimensionMismatchException, NumberIsTooLargeException {
        // Safety check.
        MatrixUtils.checkMultiplicationCompatible(this, m);

        final int outCols = m.getColumnDimension();
        OpenMapBigRealMatrix out = new OpenMapBigRealMatrix(rows, outCols);
        for (OpenLongToDoubleHashMap.Iterator iterator = entries.iterator(); iterator.hasNext();) {
            iterator.advance();
            final double value = iterator.value();
            final long key      = iterator.key();
            final int i        = (int) (key / columns);
            final int k        = (int) (key % columns);
            for (int j = 0; j < outCols; ++j) {
                final long rightKey = m.computeKey(k, j);
                if (m.entries.containsKey(rightKey)) {
                    final long outKey = out.computeKey(i, j);
                    final double outValue =
                        out.entries.get(outKey) + value * m.entries.get(rightKey);
                    if (outValue == 0.0) {
                        out.entries.remove(outKey);
                    } else {
                        out.entries.put(outKey, outValue);
                    }
                }
            }
        }

        return out;
    }

    /** {@inheritDoc} */
    @Override
    public double getEntry(int row, int column) throws OutOfRangeException {
        if(row<0||row>=getRowDimension()) {
            throw new OutOfRangeException(row,0,getRowDimension());

        } else if(column<0||column>=getColumnDimension()) {
            throw new OutOfRangeException(column,0,getColumnDimension());
        }
        MatrixUtils.checkRowIndex(this, row);
        MatrixUtils.checkColumnIndex(this, column);
        return entries.get(computeKey(row, column));
    }

    /** {@inheritDoc} */
    @Override
    public int getRowDimension() {
        return rows;
    }

    /** {@inheritDoc} */
    @Override
    public void setEntry(int row, int column, double value)
        throws OutOfRangeException {
        MatrixUtils.checkRowIndex(this, row);
        MatrixUtils.checkColumnIndex(this, column);
        synchronized (entries) {
            if (value == 0.0) {
                entries.remove(computeKey(row, column));
            } else {
                entries.put(computeKey(row, column), value);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void addToEntry(int row, int column, double increment)
        throws OutOfRangeException {
        MatrixUtils.checkRowIndex(this, row);
        MatrixUtils.checkColumnIndex(this, column);
        final long key = computeKey(row, column);
        synchronized (entries) {
            final double value = entries.get(key) + increment;
            if (value == 0.0) {
                entries.remove(key);
            } else {
                entries.put(key, value);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void multiplyEntry(int row, int column, double factor)
        throws OutOfRangeException {
        MatrixUtils.checkRowIndex(this, row);
        MatrixUtils.checkColumnIndex(this, column);
        final long key = computeKey(row, column);
        synchronized (entries) {
            final double value = entries.get(key) * factor;
            if (value == 0.0) {
                entries.remove(key);
            } else {
                entries.put(key, value);
            }
        }
    }

    /**
     * Compute the key to access a matrix element
     * @param row row index of the matrix element
     * @param column column index of the matrix element
     * @return key within the map to access the matrix element
     */
    private long computeKey(int row, int column) {
        return row * columns + column;
    }


    public static void main(String[] args) {
        OpenMapBigRealMatrix matrix = new OpenMapBigRealMatrix(60000,60000);
        matrix.addToEntry(235,2362,203956);
        System.out.println(matrix.getEntry(235,2362));
        System.out.println(matrix.getEntry(0,2362));

    }
}
