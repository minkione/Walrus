/*
 * Copyright 2018 Daniel Underhay & Matthew Daley.
 *
 * This file is part of Walrus.
 *
 * Walrus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Walrus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Walrus.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.bugfuzz.android.projectwalrus.card.carddata;

import android.arch.core.util.Function;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;

import com.bugfuzz.android.projectwalrus.R;
import com.bugfuzz.android.projectwalrus.card.carddata.ui.StaticKeyMifareReadAttemptDialogFragment;
import com.bugfuzz.android.projectwalrus.util.MiscUtils;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

@MifareReadAttempt.Metadata(
        layoutId = R.layout.layout_static_key_mifare_read_attempt,
        dialogFragment = StaticKeyMifareReadAttemptDialogFragment.class
)
public class StaticKeyMifareReadAttempt extends MifareReadAttempt {

    // TODO: single-block reads
    public final Set<MifareCardData.SectorNumber> sectorNumbers;

    public final MifareCardData.Key key;
    public final MifareCardData.KeySlot keySlot;

    public StaticKeyMifareReadAttempt(Set<MifareCardData.SectorNumber> sectorNumbers,
            MifareCardData.Key key, MifareCardData.KeySlot keySlot) {
        if (sectorNumbers.isEmpty()) {
            throw new IllegalArgumentException("Empty sector set");
        }

        if (key == null) {
            throw new IllegalArgumentException("Null key");
        }

        if (keySlot == null) {
            throw new IllegalArgumentException("Null keySlot");
        }

        this.sectorNumbers = Collections.unmodifiableSet(sectorNumbers);
        this.key = key;
        this.keySlot = keySlot;
    }

    public SpannableStringBuilder getDescription(Context context) {
        SpannableStringBuilder builder = new SpannableStringBuilder();

        // TODO XXX: i18n (w/ proper pluralisation)

        MiscUtils.appendAndSetSpan(builder, "Sector(s): ",
                new StyleSpan(android.graphics.Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.append(MiscUtils.unparseIntRanges(sectorNumbers,
                new Function<MifareCardData.SectorNumber, Integer>() {
                    @Override
                    public Integer apply(MifareCardData.SectorNumber input) {
                        return input.number;
                    }
                }));
        builder.append('\n');

        MiscUtils.appendAndSetSpan(builder, "Key: ",
                new StyleSpan(android.graphics.Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.append(key.toString());
        builder.append('\n');

        MiscUtils.appendAndSetSpan(builder, "Slot(s): ",
                new StyleSpan(android.graphics.Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.append(keySlot == MifareCardData.KeySlot.BOTH ? context.getString(R.string.both) :
                keySlot.toString());

        return builder;
    }

    @Override
    public <T> T accept(Visitor<T> visitor) throws IOException {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        StaticKeyMifareReadAttempt that = (StaticKeyMifareReadAttempt) o;

        return new EqualsBuilder()
                .append(sectorNumbers, that.sectorNumbers)
                .append(key, that.key)
                .append(keySlot, that.keySlot)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(sectorNumbers)
                .append(key)
                .append(keySlot)
                .toHashCode();
    }
}
