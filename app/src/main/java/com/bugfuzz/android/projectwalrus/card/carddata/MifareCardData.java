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

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;

import com.bugfuzz.android.projectwalrus.R;
import com.bugfuzz.android.projectwalrus.util.MiscUtils;
import com.google.common.io.BaseEncoding;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// TODO XXX: check all checks made on all constructors here
@CardData.Metadata(
        name = "MIFARE",
        iconId = R.drawable.drawable_mifare
)
public class MifareCardData extends ISO14443ACardData {

    public final Map<SectorNumber, Sector> sectors;
    @Nullable
    public SectorNumber maxSector;

    // TODO: read attempt history

    public MifareCardData() {
        sectors = new HashMap<>();
        maxSector = new SectorNumber(0);
    }

    public MifareCardData(short atqa, BigInteger uid, byte sak, byte[] ats,
            @Nullable Map<SectorNumber, Sector> sectors, @Nullable SectorNumber maxSector) {
        super(atqa, uid, sak, ats);

        this.sectors = sectors != null ? sectors : new HashMap<SectorNumber, Sector>();
        this.maxSector = maxSector;
    }

    @SuppressWarnings("unused")
    public static MifareCardData newDebugInstance() {
        return new MifareCardData((short) 0x0004, new BigInteger(32, new Random()), (byte) 0x08,
                new byte[]{}, null, new SectorNumber(0));
    }

    // TODO: XXX this
    @Override
    public String getHumanReadableText() {
        StringBuilder sb = new StringBuilder(super.getHumanReadableText());
        sb.append(": ");
        sb.append(getTypeDetailInfo());
        sb.append(" - ");

        boolean first = true;
        for (Map.Entry<SectorNumber, Sector> entry : sectors.entrySet()) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }

            sb.append(entry.getKey()).append(": ").append(entry.getValue());
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MifareCardData that = (MifareCardData) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(sectors, that.sectors)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .appendSuper(super.hashCode())
                .append(sectors)
                .toHashCode();
    }

    // TODO: split to KeySlot & KeySlots
    public enum KeySlot {
        A,
        B,
        BOTH;

        public boolean hasSlotA() {
            return this == A || this == BOTH;
        }

        public boolean hasSlotB() {
            return this == B || this == BOTH;
        }

        public List<KeySlot> getKeySlots() {
            List<KeySlot> keySlots = new ArrayList<>();

            if (hasSlotA()) {
                keySlots.add(A);
            }
            if (hasSlotB()) {
                keySlots.add(B);
            }

            return keySlots;
        }
    }

    public static class Sector implements Serializable {

        public final byte[] data;

        // TODO XXX: >4-block sectors
        public Sector(@Size(64) byte[] data) {
            if (data.length != 64) {
                throw new IllegalArgumentException("Invalid data length");
            }

            this.data = data;
        }

        @Override
        public String toString() {
            return MiscUtils.bytesToHex(data, false);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Sector sector = (Sector) o;

            return new EqualsBuilder()
                    .append(data, sector.data)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(data)
                    .toHashCode();
        }
    }

    public static class Key implements Serializable {

        public final byte[] key;

        public Key(@Size(6) byte[] key) {
            if (key.length != 6) {
                throw new IllegalArgumentException("Invalid key length");
            }

            this.key = key;
        }

        public static Key fromString(String value) {
            return new Key(BaseEncoding.base16().decode(value.toUpperCase()));
        }

        @Override
        public String toString() {
            return MiscUtils.bytesToHex(key, false);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Key key1 = (Key) o;

            return new EqualsBuilder()
                    .append(key, key1.key)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(key)
                    .toHashCode();
        }
    }

    public static class SectorNumber implements Serializable, Comparable<SectorNumber> {

        public final int number;

        public SectorNumber(@IntRange(from = 0, to = 39) int number) {
            if (number < 0 || number > 39) {
                throw new IllegalArgumentException("Invalid sector number");
            }

            this.number = number;
        }

        @Override
        public String toString() {
            return "" + number;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SectorNumber that = (SectorNumber) o;

            return new EqualsBuilder()
                    .append(number, that.number)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(number)
                    .toHashCode();
        }

        @Override
        public int compareTo(@NonNull SectorNumber o) {
            return Integer.compare(number, o.number);
        }
    }
}
