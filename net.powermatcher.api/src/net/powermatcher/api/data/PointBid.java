package net.powermatcher.api.data;

import java.util.Arrays;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import net.powermatcher.api.Agent;

/**
 * This immutable data object represents a {@link Bid} with a {@link PricePoint} array to represent the bid curve. This
 * is used by {@link Agent}s that have to create a {@link Bid}, because it is easier to create.
 *
 * @author FAN
 * @version 2.0
 */
public class PointBid
    extends Bid
    implements Iterable<PricePoint> {

    /**
     * A builder class to create an {@link PointBid} instance.
     *
     * @author FAN
     * @version 2.0
     */
    public static final class Builder {

        /**
         * The {@link MarketBasis} of the cluster.
         */
        private final MarketBasis marketBasis;

        /**
         * The set of {@link PointBid} values that make up the bid curve.
         */
        private final SortedSet<PricePoint> pricePoints;

        /**
         * Constructor to create an instance of this class.
         *
         * @param marketBasis
         *            the {@link MarketBasis} of the cluster.
         */
        public Builder(final MarketBasis marketBasis) {
            this.marketBasis = marketBasis;
            pricePoints = new TreeSet<PricePoint>();
        }

        /**
         * Adds the supplied pricePoint the PricePoint array.
         *
         * @param pricePoint
         *            The point to add
         * @return this instance of the Builder with the array
         */
        public Builder add(PricePoint pricePoint) {
            pricePoints.add(pricePoint);
            return this;
        }

        /**
         * Creates a PricePoint with the supplied price and demand. Adds the point to the PricePoint array.
         *
         * @param price
         *            The price of the point that should be added
         * @param demand
         *            The demand value of the point that should be added
         * @return this instance of the Builder with the array
         */
        public Builder add(double price, double demand) {
            return add(new PricePoint(marketBasis, price, demand));
        }

        /**
         * Uses the supplied parameters to create a new PointBid.
         *
         * @return The created {@link PointBid}
         * @throws IllegalArgumentException
         *             when the marketBasis is null
         */
        public PointBid build() {
            return new PointBid(marketBasis, pricePoints.toArray(new PricePoint[pricePoints.size()]));
        }
    }

    /**
     * The array of {@link PricePoint}s that make up the bid curve.
     */
    private final PricePoint[] pricePoints;

    /**
     * The {@link ArrayBid} representation of this PointBid
     */
    private transient ArrayBid arrayBid;

    /**
     * A constructor to create an instance of PointBid.
     *
     * @param marketBasis
     *            the {@link MarketBasis} of the cluster
     * @param pricePoints
     *            the {@link PointBid} Array that belongs to this bid.
     */
    public PointBid(MarketBasis marketBasis, PricePoint... pricePoints) {
        super(marketBasis);
        if (pricePoints.length == 0) {
            throw new IllegalArgumentException("At least 1 pricepoint is needed");
        }

        boolean allEqualDemand = true;
        double firstDemand = pricePoints[0].getDemand(), lastDemand = pricePoints[0].getDemand();
        for (PricePoint pricePoint : pricePoints) {
            if (!pricePoint.getPrice().getMarketBasis().equals(marketBasis)) {
                throw new IllegalArgumentException("Not all pricepoints match the given marketbasis");
            } else if (pricePoint.getDemand() > lastDemand) {
                throw new IllegalAccessError("The bid should be going down");
            } else if (pricePoint.getDemand() != firstDemand) {
                allEqualDemand = false;
            }

            lastDemand = pricePoint.getDemand();
        }

        if (allEqualDemand) {
            this.pricePoints = new PricePoint[] { pricePoints[0] };
        } else {
            this.pricePoints = pricePoints;
        }
    }

    /**
     * A constructor used to create an PointBid, based on a {@link ArrayBid}.
     *
     * @param base
     *            The {@link ArrayBid} this PointBid will be based on.
     */
    PointBid(ArrayBid base) {
        super(base.marketBasis);
        pricePoints = base.calculatePricePoints();
        arrayBid = base;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArrayBid aggregate(Bid other) {
        return toArrayBid().aggregate(other);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Price calculateIntersection(double targetDemand) {
        int leftIx = 0, rightIx = pricePoints.length - 1;

        // First test for a few special cases
        if (targetDemand > pricePoints[leftIx].getDemand()) {
            // If the target is higher than the maximum of the bid, return the minimum price
            return new Price(marketBasis, marketBasis.getMinimumPrice());
        } else if (targetDemand < pricePoints[rightIx].getDemand()) {
            // If the target is lower than the minimum of the bid, return the maximum price
            return new Price(marketBasis, marketBasis.getMaximumPrice());
        } else if (demandIsEqual(targetDemand, pricePoints[leftIx].getDemand())) {
            rightIx = leftIx;
        } else if (demandIsEqual(targetDemand, pricePoints[rightIx].getDemand())) {
            leftIx = rightIx;
        } else { // demand is between the limits of this bid, which can not be flat at this point
            // Go on while there is at least 1 point between the left and right index
            while (rightIx - leftIx > 1) {
                // Determine the middle between the 2 boundaries
                int middleIx = (leftIx + rightIx) / 2;
                double middleDemand = pricePoints[middleIx].getDemand();

                if (demandIsEqual(targetDemand, middleDemand)) {
                    // A point with the target demand is found, select this point
                    leftIx = middleIx;
                    rightIx = middleIx;
                } else if (middleDemand > targetDemand) {
                    // If the middle demand is bigger than the target demand, we set the left to the middle
                    leftIx = middleIx;
                } else { // middleDemand < targetDemand
                    // If the middle demand is smaller than the target demand, we set the right to the middle
                    rightIx = middleIx;
                }
            }
        }

        // If the left or right point matches the targetDemand, expand the range
        while (leftIx > 0 && demandIsEqual(targetDemand, pricePoints[leftIx - 1].getDemand())) {
            leftIx--;
        }
        while (rightIx < pricePoints.length - 1 && demandIsEqual(targetDemand, pricePoints[rightIx + 1].getDemand())) {
            rightIx++;
        }

        return intersect(leftIx, rightIx, targetDemand);
    }

    private Price intersect(int leftIx, int rightIx, double targetDemand) {
        PricePoint leftPoint = pricePoints[leftIx];
        PricePoint rightPoint = pricePoints[rightIx];

        double leftPrice = rightIx == 0 ? marketBasis.getMinimumPrice()
                                       : leftPoint.getPrice().getPriceValue();
        double rightPrice = leftIx == pricePoints.length - 1 ? marketBasis.getMaximumPrice()
                                                            : rightPoint.getPrice().getPriceValue();

        double leftDemand = leftPoint.getDemand();
        double rightDemand = rightPoint.getDemand();

        double demandFactor = demandIsEqual(leftDemand, rightDemand) ? 0.5
                                                                    : (leftDemand - targetDemand) / (leftDemand - rightDemand);
        double price = leftPrice + (rightPrice - leftPrice) * demandFactor;

        return new Price(marketBasis, price);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getMaximumDemand() {
        return getFirst().getDemand();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getMinimumDemand() {
        return getLast().getDemand();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArrayBid toArrayBid() {
        if (arrayBid == null) {
            arrayBid = new ArrayBid(this);
        }
        return arrayBid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PointBid toPointBid() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getDemandAt(Price price) {
        if (pricePoints.length == 1) {
            // Flat bid, send any demand (they are all the same)
            return getMaximumDemand();
        } else if (price.compareTo(getFirst().getPrice()) < 0) {
            // If the price is lower than the lowest price, return the maximum
            // demand
            return getMaximumDemand();
        } else if (price.equals(getFirst().getPrice())) {
            // If the first matcher, it could be that the second is at the same price. If that is the case, use the
            // second, otherwise the first.
            PricePoint secondPricePoint = pricePoints[1];
            if (price.equals(secondPricePoint.getPrice())) {
                return secondPricePoint.getDemand();
            } else {
                return getMaximumDemand();
            }
        } else if (price.compareTo(getLast().getPrice()) >= 0) {
            // If the price is higher than the highest price, return the minimum
            // demand
            return getMinimumDemand();
        } else {
            // We have a normal case that is somewhere in between the lower and higher demands

            // First determine which 2 pricepoints it is in between
            int lowIx = 0, highIx = pricePoints.length;
            while (highIx - lowIx > 1) {
                int middleIx = (lowIx + highIx) / 2;
                PricePoint middle = pricePoints[middleIx];

                int cmp = middle.getPrice().compareTo(price);
                if (cmp < 0) {
                    lowIx = middleIx;
                } else if (cmp > 0) {
                    highIx = middleIx;
                } else {
                    // Found at least 1 point that is equal in price.
                    // This is the special case with an open and closed node. Always the lower demand should be chosen.
                    PricePoint nextPoint = pricePoints[middleIx + 1];
                    if (price.equals(nextPoint.getPrice())) {
                        return nextPoint.getDemand();
                    } else {
                        middle.getDemand();
                    }
                }
            }
            PricePoint lower = pricePoints[lowIx];
            PricePoint higher = pricePoints[highIx];

            // Now calculate the demand between the 2 points
            // First the factor (between 0 and 1) of where the price is on the line
            double factor = (price.getPriceValue() - lower.getPrice().getPriceValue())
                            / (higher.getPrice().getPriceValue() - lower.getPrice().getPriceValue());
            // Now calculate the demand
            return (1 - factor) * lower.getDemand() + factor * higher.getDemand();
        }
    }

    /**
     * @return the first {@link PricePoint} in the bid curve.
     */
    private PricePoint getFirst() {
        return pricePoints[0];
    }

    /**
     * @return the last {@link PricePoint} in the bid curve.
     */
    private PricePoint getLast() {
        return pricePoints[pricePoints.length - 1];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<PricePoint> iterator() {
        return new Iterator<PricePoint>() {
            private int nextIndex;

            /**
             * {@inheritDoc}
             */
            @Override
            public PricePoint next() {
                return pricePoints[nextIndex++];
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean hasNext() {
                return nextIndex < pricePoints.length;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * @return a copy of pricePoints.
     */
    public PricePoint[] getPricePoints() {
        return Arrays.copyOf(pricePoints, pricePoints.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return 2011 * Arrays.deepHashCode(pricePoints) + marketBasis.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || !(obj instanceof PointBid)) {
            return false;
        } else {
            PointBid other = ((PointBid) obj);
            return marketBasis.equals(other.marketBasis)
                   && Arrays.equals(other.getPricePoints(), getPricePoints());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("PointBid [");

        for (PricePoint point : pricePoints) {
            b.append(point).append(',');
        }

        b.setLength(b.length() - 1);
        b.append('}');
        return b.toString();
    }

}
