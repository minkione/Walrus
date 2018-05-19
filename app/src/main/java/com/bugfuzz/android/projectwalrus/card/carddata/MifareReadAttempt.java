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

import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;

import com.bugfuzz.android.projectwalrus.card.carddata.ui.MifareReadAttemptDialogFragment;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public abstract class MifareReadAttempt implements Serializable {

    public static MifareReadAttemptDialogFragment createDialogFragment(
            Class<? extends MifareReadAttempt> readAttemptClass,
            @Nullable MifareReadAttempt readAttempt, int callbackId) {
        MifareReadAttempt.Metadata metadata = readAttemptClass.getAnnotation(
                MifareReadAttempt.Metadata.class);

        MifareReadAttemptDialogFragment dialog;
        try {
            dialog = metadata.dialogFragment().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        if (readAttempt != null && !readAttemptClass.isInstance(readAttempt)) {
            throw new RuntimeException("Invalid read attempt / read attempt class");
        }

        Bundle args = new Bundle();
        args.putSerializable("read_attempt", readAttempt);
        args.putInt("callback_id", callbackId);
        dialog.setArguments(args);

        return dialog;
    }

    public abstract <T> T accept(Visitor<T> visitor) throws IOException;

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Metadata {
        @LayoutRes
        int layoutId();

        Class<? extends MifareReadAttemptDialogFragment> dialogFragment();
    }

    public interface Visitor<T> {
        T visit(StaticKeyMifareReadAttempt staticKeyMifareReadAttempt) throws IOException;
    }
}
