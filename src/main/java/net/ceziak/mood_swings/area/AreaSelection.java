package net.ceziak.mood_swings.area;

import net.minecraft.util.math.BlockPos;

public final class AreaSelection {
    private BlockPos first;
    private BlockPos second;
    private String firstDimension;
    private String secondDimension;

    public BlockPos first() {
        return first;
    }

    public BlockPos second() {
        return second;
    }

    public String dimension() {
        return firstDimension;
    }

    public void setFirst(BlockPos first, String dimension) {
        this.first = first.toImmutable();
        this.firstDimension = dimension;
        if (this.second != null && !dimension.equals(this.secondDimension)) {
            this.second = null;
            this.secondDimension = null;
        }
    }

    public void setSecond(BlockPos second, String dimension) {
        this.second = second.toImmutable();
        this.secondDimension = dimension;
        if (this.first != null && !dimension.equals(this.firstDimension)) {
            this.first = null;
            this.firstDimension = null;
        }
    }

    public boolean isComplete() {
        return first != null
                && second != null
                && firstDimension != null
                && firstDimension.equals(secondDimension);
    }
}
