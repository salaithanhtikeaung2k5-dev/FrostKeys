/*
 * Myanmar pre-base vowel (ေ, U+1031) visual-order -> logical-order reorderer.
 *
 * Many users type Myanmar in "visual order", pressing ေ before the
 * consonant/medials it visually precedes on screen:
 *
 *     keys pressed:      ေ   <consonant>   [medial]
 *     required storage:  <consonant> [medial] ေ
 *
 * A correct Myanmar shaping engine still *renders* that stored order with
 * ေ appearing to the left of the base consonant, so fixing storage order
 * does not change what the user sees on screen -- it only fixes what
 * actually gets written to the text field.
 *
 * This class buffers a "fresh" ေ (one not already following a consonant or
 * medial) and swaps it back into place, one delete+recommit at a time, as
 * the following consonant and medial(s) arrive.
 */
package helium314.keyboard.latin.inputlogic;

import helium314.keyboard.latin.RichInputConnection;

public final class MyanmarReorderHelper {

    /** MYANMAR VOWEL SIGN E */
    private static final int VOWEL_E = 0x1031;

    /** Consonant block, per project spec (U+1000 - U+1021) */
    private static final int CONSONANT_START = 0x1000;
    private static final int CONSONANT_END = 0x1021;

    /** Medials: Ya, Ra, Wa, Ha */
    private static final int MEDIAL_YA = 0x103B;
    private static final int MEDIAL_RA = 0x103C;
    private static final int MEDIAL_WA = 0x103D;
    private static final int MEDIAL_HA = 0x103E;

    /** A lone ေ has been committed and is waiting for its consonant/medials. */
    private boolean mPendingVowelE = false;

    /**
     * ေ has already been swapped after a consonant (and maybe medials) and is
     * currently the last committed character, so another medial should still
     * trigger one more swap (handles stacked medials like ြွ).
     */
    private boolean mVowelAlreadyPlaced = false;

    private static boolean isConsonant(final int cp) {
        return cp >= CONSONANT_START && cp <= CONSONANT_END;
    }

    private static boolean isMedial(final int cp) {
        return cp == MEDIAL_YA || cp == MEDIAL_RA || cp == MEDIAL_WA || cp == MEDIAL_HA;
    }

    private static String s(final int codePoint) {
        return String.valueOf((char) codePoint);
    }

    /**
     * Call whenever a pending reorder should be abandoned instead of
     * continued: start of a new input session, a genuine user-initiated
     * cursor/selection move, or a backspace. See integration notes for
     * exactly where to call this.
     */
    public void reset() {
        mPendingVowelE = false;
        mVowelAlreadyPlaced = false;
    }

    /**
     * Call for every plain printable codePoint InputLogic is about to commit.
     *
     * @return true if this helper already updated {@code connection} for
     *         {@code codePoint} (the caller must NOT also commit it normally);
     *         false if the caller should proceed with its normal commit path.
     */
    public boolean handleCodePoint(final int codePoint, final RichInputConnection connection) {

        if (codePoint == VOWEL_E && !mPendingVowelE && !mVowelAlreadyPlaced) {
            // Only buffer a ေ that is starting *fresh*. If it's typed right
            // after a consonant/medial the user already entered in correct
            // order, it's already in the right place -- buffering it anyway
            // would incorrectly swap it with the *next* syllable's consonant.
            final CharSequence before = connection.getTextBeforeCursor(1, 0);
            final int prevCp = (before != null && before.length() > 0)
                    ? before.charAt(before.length() - 1) : -1;
            if (isConsonant(prevCp) || isMedial(prevCp)) {
                return false; // already correctly placed; commit normally
            }
            connection.commitText(s(VOWEL_E), 1);
            mPendingVowelE = true;
            return true;
        }

        if (mPendingVowelE && isConsonant(codePoint)) {
            connection.beginBatchEdit();
            connection.deleteSurroundingText(1, 0); // remove the lone ေ
            connection.commitText(s(codePoint) + s(VOWEL_E), 1);
            connection.endBatchEdit();
            mPendingVowelE = false;
            mVowelAlreadyPlaced = true;
            return true;
        }

        if (mVowelAlreadyPlaced && isMedial(codePoint)) {
            connection.beginBatchEdit();
            connection.deleteSurroundingText(1, 0); // remove the trailing ေ
            connection.commitText(s(codePoint) + s(VOWEL_E), 1);
            connection.endBatchEdit();
            // mVowelAlreadyPlaced stays true: a second medial (e.g. ြွ) should
            // still trigger this same swap again.
            return true;
        }

        // Any other key (another vowel sign, asat, space, punctuation, a new
        // consonant with nothing pending, ...) closes out the pending reorder
        // without touching this keystroke.
        reset();
        return false;
    }
}
