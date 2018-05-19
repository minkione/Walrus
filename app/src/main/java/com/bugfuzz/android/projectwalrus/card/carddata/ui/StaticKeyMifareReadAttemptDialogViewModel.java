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

package com.bugfuzz.android.projectwalrus.card.carddata.ui;

import android.arch.core.util.Function;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.bugfuzz.android.projectwalrus.card.carddata.MifareCardData;
import com.bugfuzz.android.projectwalrus.card.carddata.MifareReadAttempt;
import com.bugfuzz.android.projectwalrus.card.carddata.StaticKeyMifareReadAttempt;
import com.bugfuzz.android.projectwalrus.util.MiscUtils;

public class StaticKeyMifareReadAttemptDialogViewModel extends ViewModel {

    public final MutableLiveData<String> sectors = new MutableLiveData<>();
    public final MutableLiveData<String> key = new MutableLiveData<>();
    public final MutableLiveData<MifareCardData.KeySlot> keySlot = new MutableLiveData<>();

    private final MediatorLiveData<Boolean> isValid = new MediatorLiveData<>();

    private final MutableLiveData<MifareReadAttempt> result = new MutableLiveData<>();

    public StaticKeyMifareReadAttemptDialogViewModel(
            @Nullable StaticKeyMifareReadAttempt staticKeyMifareReadAttempt) {
        Observer updateValidity = new Observer() {
            @Override
            public void onChanged(@Nullable Object ignored) {
                try {
                    createReadAttempt();
                } catch (IllegalArgumentException exception) {
                    isValid.setValue(false);
                    return;
                }

                isValid.setValue(true);
            }
        };

        // noinspection unchecked
        isValid.addSource(sectors, updateValidity);
        // noinspection unchecked
        isValid.addSource(key, updateValidity);
        // noinspection unchecked
        isValid.addSource(keySlot, updateValidity);

        if (staticKeyMifareReadAttempt != null) {
            sectors.setValue(MiscUtils.unparseIntRanges(staticKeyMifareReadAttempt.sectorNumbers,
                    new Function<MifareCardData.SectorNumber, Integer>() {
                        @Override
                        public Integer apply(MifareCardData.SectorNumber input) {
                            return input.number;
                        }
                    }));
            key.setValue(staticKeyMifareReadAttempt.key.toString());
            keySlot.setValue(staticKeyMifareReadAttempt.keySlot);
        } else {
            sectors.setValue("");
            key.setValue("");
        }
    }

    public LiveData<Boolean> getIsValid() {
        return isValid;
    }

    public LiveData<MifareReadAttempt> getResult() {
        return result;
    }

    public void onSlotCheckedChanged(MifareCardData.KeySlot changedSlot, boolean isChecked) {
        boolean hasNewSlotA = changedSlot == MifareCardData.KeySlot.A ? isChecked :
                keySlot.getValue() != null && keySlot.getValue().hasSlotA();
        boolean hasNewSlotB = changedSlot == MifareCardData.KeySlot.B ? isChecked :
                keySlot.getValue() != null && keySlot.getValue().hasSlotB();

        MifareCardData.KeySlot newSlot;
        if (hasNewSlotA && hasNewSlotB) {
            newSlot = MifareCardData.KeySlot.BOTH;
        } else if (hasNewSlotA) {
            newSlot = MifareCardData.KeySlot.A;
        } else if (hasNewSlotB) {
            newSlot = MifareCardData.KeySlot.B;
        } else {
            newSlot = null;
        }

        keySlot.setValue(newSlot);
    }

    private MifareReadAttempt createReadAttempt() {
        return new StaticKeyMifareReadAttempt(
                MiscUtils.parseIntRanges(sectors.getValue(),
                        new Function<Integer, MifareCardData.SectorNumber>() {
                            @Override
                            public MifareCardData.SectorNumber apply(Integer input) {
                                return new MifareCardData.SectorNumber(input);
                            }
                        }),
                MifareCardData.Key.fromString(key.getValue()), keySlot.getValue());
    }

    public void onAddClick() {
        result.setValue(createReadAttempt());
    }

    public static class Factory implements ViewModelProvider.Factory {

        @Nullable
        private final StaticKeyMifareReadAttempt staticKeyMifareReadAttempt;

        public Factory(@Nullable StaticKeyMifareReadAttempt staticKeyMifareReadAttempt) {
            this.staticKeyMifareReadAttempt = staticKeyMifareReadAttempt;
        }

        @Override
        @NonNull
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass != StaticKeyMifareReadAttemptDialogViewModel.class) {
                throw new RuntimeException("Invalid view model class requested");
            }

            // noinspection unchecked
            return (T) new StaticKeyMifareReadAttemptDialogViewModel(staticKeyMifareReadAttempt);
        }
    }
}
