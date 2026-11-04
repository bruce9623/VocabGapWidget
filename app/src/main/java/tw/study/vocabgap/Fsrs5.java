package tw.study.vocabgap;

/**
 * FSRS-5, 19 public default weights, 90% desired retention.
 * Single-device baseline without per-user parameter fitting or random interval fuzz.
 * The UI supplies Again=1, Hard=2, Good=3, Easy=4.
 */
public final class Fsrs5 {
    private Fsrs5() {}
    private static final double[] W = {
        0.40255, 1.18385, 3.173, 15.69105, 7.1949,
        0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
        1.01925, 1.9395, 0.11, 0.29605, 2.2698,
        0.2315, 2.9898, 0.51655, 0.6621
    };
    public static final long DAY = 86_400_000L;

    public static final class State {
        public final double stability;
        public final double difficulty;
        public final long due;
        State(double s, double d, long when) { stability=s; difficulty=d; due=when; }
    }
    private static double clip(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    private static double initialDifficulty(int grade) {
        return clip(W[4] - Math.exp(W[5] * (grade-1)) + 1, 1, 10);
    }
    public static State review(boolean hasHistory, double s, double d, long previousReview,
                               int grade, long now) {
        if (grade < 1 || grade > 4) throw new IllegalArgumentException("grade 1..4");
        double nextS;
        double nextD;
        if (!hasHistory || s <= 0 || d <= 0) {
            nextS = W[grade-1];
            nextD = initialDifficulty(grade);
        } else {
            double elapsedDays = Math.max(0, Math.floor((double)(now - previousReview) / DAY));
            double r = Math.pow(1 + 19.0/81.0 * elapsedDays / s, -0.5);
            double delta = -W[6] * (grade - 3);
            double dTemp = d + delta * (10 - d) / 9;
            nextD = clip(W[7] * initialDifficulty(4) + (1 - W[7]) * dTemp, 1, 10);
            if (elapsedDays == 0) {
                nextS = s * Math.exp(W[17] * (grade - 3 + W[18]));
            } else if (grade == 1) {
                double candidate = W[11] * Math.pow(d, -W[12])
                    * (Math.pow(s+1, W[13]) - 1) * Math.exp(W[14] * (1-r));
                nextS = Math.min(candidate, s * Math.exp(-W[17] * W[18]));
            } else {
                double hardMultiplier = grade == 2 ? W[15] : 1;
                double easyMultiplier = grade == 4 ? W[16] : 1;
                nextS = s * (1 + Math.exp(W[8]) * (11-d) * Math.pow(s, -W[9])
                    * (Math.exp(W[10]*(1-r)) - 1) * hardMultiplier * easyMultiplier);
            }
        }
        nextS = clip(nextS, 0.01, 36500);
        // Relearning: an Again card returns after ten minutes, even though FSRS
        // stability (used for later long-term review) is also updated.
        long interval = grade == 1 ? 10 * 60_000L :
            Math.max(DAY, Math.round(nextS * DAY));
        return new State(nextS, nextD, now + interval);
    }
}
