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

package com.bugfuzz.android.projectwalrus.device.proxmark3;

import android.arch.lifecycle.Observer;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AppCompatActivity;
import android.util.Pair;

import com.bugfuzz.android.projectwalrus.R;
import com.bugfuzz.android.projectwalrus.card.carddata.CardData;
import com.bugfuzz.android.projectwalrus.card.carddata.HIDCardData;
import com.bugfuzz.android.projectwalrus.card.carddata.MifareCardData;
import com.bugfuzz.android.projectwalrus.card.carddata.MifareReadAttempt;
import com.bugfuzz.android.projectwalrus.card.carddata.StaticKeyMifareReadAttempt;
import com.bugfuzz.android.projectwalrus.card.carddata.ui.MifareReadSetupDialogFragment;
import com.bugfuzz.android.projectwalrus.device.CardDevice;
import com.bugfuzz.android.projectwalrus.device.ReadCardDataOperation;
import com.bugfuzz.android.projectwalrus.device.UsbCardDevice;
import com.bugfuzz.android.projectwalrus.device.UsbSerialCardDevice;
import com.bugfuzz.android.projectwalrus.device.WriteOrEmulateCardDataOperation;
import com.bugfuzz.android.projectwalrus.device.proxmark3.ui.Proxmark3Activity;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import org.apache.commons.lang3.ArrayUtils;
import org.parceler.Parcel;
import org.parceler.ParcelConstructor;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CardDevice.Metadata(
        name = "Proxmark3",
        iconId = R.drawable.drawable_proxmark3,
        supportsRead = {HIDCardData.class, MifareCardData.class},
        supportsWrite = {HIDCardData.class},
        supportsEmulate = {}
)
@UsbCardDevice.UsbIds({
        @UsbCardDevice.UsbIds.Ids(vendorId = 0x2d2d, productId = 0x504d), // CDC Proxmark3
        @UsbCardDevice.UsbIds.Ids(vendorId = 0x9ac4, productId = 0x4b8f), // HID Proxmark3
        @UsbCardDevice.UsbIds.Ids(vendorId = 0x502d, productId = 0x502d)  // Proxmark3 Easy(?)
})
public class Proxmark3Device extends UsbSerialCardDevice<Proxmark3Command>
        implements CardDevice.Versioned {

    private static final long DEFAULT_TIMEOUT = 20 * 1000;
    private static final Pattern TAG_ID = Pattern.compile("TAG ID: ([0-9a-fA-F]+)");

    private final Semaphore semaphore = new Semaphore(1);

    public Proxmark3Device(Context context, UsbDevice usbDevice) throws IOException {
        super(context, usbDevice, context.getString(R.string.idle));

        send(new Proxmark3Command(Proxmark3Command.VERSION));
    }

    @Override
    protected void setupSerialParams(UsbSerialDevice usbSerialDevice) {
        usbSerialDevice.setBaudRate(115200);

        usbSerialDevice.setDataBits(UsbSerialInterface.DATA_BITS_8);
        usbSerialDevice.setParity(UsbSerialInterface.PARITY_NONE);
        usbSerialDevice.setStopBits(UsbSerialInterface.STOP_BITS_1);

        usbSerialDevice.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
    }

    @Override
    protected Pair<Proxmark3Command, Integer> sliceIncoming(byte[] in) {
        if (in.length < Proxmark3Command.getByteLength()) {
            return null;
        }

        return new Pair<>(Proxmark3Command.fromBytes(in), Proxmark3Command.getByteLength());
    }

    @Override
    protected byte[] formatOutgoing(Proxmark3Command out) {
        return out.toBytes();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean tryAcquireAndSetStatus(String status) {
        if (!semaphore.tryAcquire()) {
            return false;
        }

        setStatus(status);

        return true;
    }

    private void releaseAndSetStatus() {
        setStatus(context.getString(R.string.idle));
        semaphore.release();
    }

    private <O> O sendThenReceiveCommands(Proxmark3Command out,
            ReceiveSink<Proxmark3Command, O> receiveSink) throws IOException {
        setReceiving(true);

        try {
            send(out);
            return receive(receiveSink);
        } finally {
            setReceiving(false);
        }
    }

    @Override
    @UiThread
    public void createReadCardDataOperation(final AppCompatActivity activity,
            Class<? extends CardData> cardDataClass, final int callbackId) {
        ensureOperationCreatedCallbackSupported(activity);

        if (cardDataClass == HIDCardData.class) {
            ((OnOperationCreatedCallback) activity).onOperationCreated(new ReadHIDOperation(this),
                    callbackId);
        } else if (cardDataClass == MifareCardData.class) {
            final MifareReadSetupDialogFragment dialog = MifareReadSetupDialogFragment.create(
                    callbackId);

            dialog.show(activity.getSupportFragmentManager(),
                    "proxmark3_device_mifare_read_setup_dialog");
            activity.getSupportFragmentManager().executePendingTransactions();

            dialog.getViewModel().getSelectedReadAttempts().observeForever(
                    new Observer<List<MifareReadAttempt>>() {
                        @Override
                        public void onChanged(@Nullable List<MifareReadAttempt> readAttempts) {
                            ((OnOperationCreatedCallback) activity).onOperationCreated(
                                    new ReadMifareOperation(Proxmark3Device.this, readAttempts),
                                    callbackId);
                        }
                    });
        } else {
            throw new RuntimeException("Invalid card data class");
        }
    }

    @Override
    @UiThread
    public void createWriteOrEmulateDataOperation(AppCompatActivity activity, CardData cardData,
            boolean write, int callbackId) {
        ensureOperationCreatedCallbackSupported(activity);

        ((OnOperationCreatedCallback) activity).onOperationCreated(
                new WriteOrEmulateHIDOperation(this, cardData, write), callbackId);
    }

    @Override
    public Intent getDeviceActivityIntent(Context context) {
        return Proxmark3Activity.getStartActivityIntent(context, this);
    }

    @Override
    public String getVersion() throws IOException {
        if (!tryAcquireAndSetStatus(context.getString(R.string.getting_version))) {
            throw new IOException(context.getString(R.string.device_busy));
        }

        try {
            Proxmark3Command version = sendThenReceiveCommands(
                    new Proxmark3Command(Proxmark3Command.VERSION),
                    new CommandWaiter(Proxmark3Command.ACK, DEFAULT_TIMEOUT));
            if (version == null) {
                throw new IOException(context.getString(R.string.get_version_timeout));
            }

            return version.dataAsString();
        } finally {
            releaseAndSetStatus();
        }
    }

    public TuneResult tune(boolean lf, boolean hf) throws IOException {
        if (!tryAcquireAndSetStatus(context.getString(R.string.tuning))) {
            throw new IOException(context.getString(R.string.device_busy));
        }

        try {
            long arg = 0;
            if (lf) {
                arg |= Proxmark3Command.MEASURE_ANTENNA_TUNING_FLAG_TUNE_LF;
            }
            if (hf) {
                arg |= Proxmark3Command.MEASURE_ANTENNA_TUNING_FLAG_TUNE_HF;
            }
            if (arg == 0) {
                throw new IllegalArgumentException("Must tune LF or HF");
            }

            Proxmark3Command result = sendThenReceiveCommands(
                    new Proxmark3Command(Proxmark3Command.MEASURE_ANTENNA_TUNING,
                            new long[]{arg, 0, 0}),
                    new CommandWaiter(Proxmark3Command.MEASURED_ANTENNA_TUNING, DEFAULT_TIMEOUT));
            if (result == null) {
                throw new IOException(context.getString(R.string.tune_timeout));
            }

            float[] lfVoltages = new float[256];
            for (int i = 0; i < 256; ++i) {
                lfVoltages[i] = ((result.data[i] & 0xff) << 8) / 1e3f;
            }

            return new TuneResult(
                    lf, hf,
                    lf ? lfVoltages : null,
                    lf ? (result.args[0] & 0xffff) / 1e3f : null,
                    lf ? (result.args[0] >>> 16) / 1e3f : null,
                    lf ? 12e6f / ((result.args[2] & 0xffff) + 1) : null,
                    lf ? (result.args[2] >>> 16) / 1e3f : null,
                    hf ? (result.args[1] & 0xffff) / 1e3f : null);
        } finally {
            releaseAndSetStatus();
        }
    }

    private static class ReadHIDOperation extends ReadCardDataOperation {

        ReadHIDOperation(CardDevice cardDevice) {
            super(cardDevice);
        }

        @Override
        @WorkerThread
        public void execute(Context context, final ShouldContinueCallback shouldContinueCallback,
                final ResultSink resultSink) throws IOException {
            Proxmark3Device proxmark3Device = (Proxmark3Device) getCardDeviceOrThrow();

            if (!proxmark3Device.tryAcquireAndSetStatus(context.getString(R.string.reading))) {
                throw new IOException(context.getString(R.string.device_busy));
            }

            try {
                proxmark3Device.setReceiving(true);

                try {
                    proxmark3Device.send(new Proxmark3Command(Proxmark3Command.HID_DEMOD_FSK,
                            new long[]{0, 0, 0}));

                    // TODO: do periodic VERSION-based device-aliveness checking like Chameleon
                    // Mini will/does
                    proxmark3Device.receive(new ReceiveSink<Proxmark3Command, Boolean>() {
                        @Override
                        public Boolean onReceived(Proxmark3Command in) {
                            if (in.op != Proxmark3Command.DEBUG_PRINT_STRING) {
                                return null;
                            }

                            String dataAsString = in.dataAsString();

                            if (dataAsString.equals("Stopped")) {
                                return true;
                            }

                            Matcher matcher = TAG_ID.matcher(dataAsString);
                            if (matcher.find() && resultSink != null) {
                                resultSink.onResult(new HIDCardData(new BigInteger(
                                        matcher.group(1), 16)));
                            }

                            return null;
                        }

                        @Override
                        public boolean wantsMore() {
                            return shouldContinueCallback.shouldContinue();
                        }
                    });
                } finally {
                    proxmark3Device.setReceiving(false);
                }

                proxmark3Device.send(new Proxmark3Command(Proxmark3Command.VERSION));
            } finally {
                proxmark3Device.releaseAndSetStatus();
            }
        }

        @Override
        public Class<? extends CardData> getCardDataClass() {
            return HIDCardData.class;
        }
    }

    private static class WriteOrEmulateHIDOperation extends WriteOrEmulateCardDataOperation {

        WriteOrEmulateHIDOperation(CardDevice cardDevice, CardData cardData, boolean write) {
            super(cardDevice, cardData, write);
        }

        @Override
        @WorkerThread
        public void execute(Context context, ShouldContinueCallback shouldContinueCallback)
                throws IOException {
            if (!isWrite()) {
                throw new RuntimeException("Can't emulate");
            }

            Proxmark3Device proxmark3Device = (Proxmark3Device) getCardDeviceOrThrow();

            if (!proxmark3Device.tryAcquireAndSetStatus(context.getString(R.string.writing))) {
                throw new IOException(context.getString(R.string.device_busy));
            }

            try {
                HIDCardData hidCardData = (HIDCardData) getCardData();

                if (!proxmark3Device.sendThenReceiveCommands(
                        new Proxmark3Command(
                                Proxmark3Command.HID_CLONE_TAG,
                                new long[]{
                                        hidCardData.data.shiftRight(64).intValue(),
                                        hidCardData.data.shiftRight(32).intValue(),
                                        hidCardData.data.intValue()
                                },
                                new byte[]{hidCardData.data.bitLength() > 44 ? (byte) 1 : 0}),
                        new WatchdogReceiveSink<Proxmark3Command, Boolean>(DEFAULT_TIMEOUT) {
                            @Override
                            public Boolean onReceived(Proxmark3Command in) {
                                return in.op == Proxmark3Command.DEBUG_PRINT_STRING
                                        && in.dataAsString().equals("DONE!") ? true : null;
                            }
                        })) {
                    throw new IOException(context.getString(R.string.write_card_timeout));
                }
            } finally {
                proxmark3Device.releaseAndSetStatus();
            }
        }
    }

    private static class ReadMifareOperation extends ReadCardDataOperation {

        private final List<MifareReadAttempt> readAttempts;

        ReadMifareOperation(CardDevice cardDevice, List<MifareReadAttempt> readAttempts) {
            super(cardDevice);

            this.readAttempts = readAttempts;
        }

        @Override
        @WorkerThread
        public void execute(Context context, final ShouldContinueCallback shouldContinueCallback,
                ResultSink resultSink) throws IOException {
            final Proxmark3Device proxmark3Device = (Proxmark3Device) getCardDeviceOrThrow();

            if (!proxmark3Device.tryAcquireAndSetStatus(context.getString(R.string.reading))) {
                throw new IOException(context.getString(R.string.device_busy));
            }

            try {
                while (shouldContinueCallback.shouldContinue()) {
                    // TODO: do periodic VERSION-based device-aliveness checking like Chameleon
                    // Mini will/does
                    Proxmark3Command result = proxmark3Device.sendThenReceiveCommands(
                            new Proxmark3Command(Proxmark3Command.READER_ISO_14443A,
                                    new long[]{Proxmark3Command.ISO14A_CONNECT, 0, 0}),
                            new CommandWaiter(Proxmark3Command.ACK, DEFAULT_TIMEOUT));

                    if (result.args[0] != 0) {
                        ByteBuffer bb = ByteBuffer.wrap(result.data);
                        bb.order(ByteOrder.LITTLE_ENDIAN);

                        byte[] uid = new byte[10];
                        bb.get(uid);
                        uid = ArrayUtils.subarray(uid, 0, bb.get());

                        short atqa = bb.getShort();

                        byte sak = bb.get();

                        byte[] ats = new byte[bb.get()];
                        bb.get(ats);

                        // TODO: max sector number
                        MifareCardData mifareCardData = new MifareCardData(atqa,
                                new BigInteger(uid), sak, ats, null, null);

                        for (MifareReadAttempt readAttempt : readAttempts) {
                            if (!shouldContinueCallback.shouldContinue()) {
                                break;
                            }

                            mifareCardData.sectors.putAll(readAttempt.accept(new ReadAttemptVisitor(
                                    proxmark3Device, shouldContinueCallback)));
                        }

                        resultSink.onResult(mifareCardData);
                    }
                }
            } finally {
                proxmark3Device.releaseAndSetStatus();
            }
        }

        @Override
        public Class<? extends CardData> getCardDataClass() {
            return MifareCardData.class;
        }

        private class ReadAttemptVisitor implements MifareReadAttempt.Visitor<
                Map<MifareCardData.SectorNumber, MifareCardData.Sector>> {

            private final Proxmark3Device proxmark3Device;
            private final ShouldContinueCallback shouldContinueCallback;

            public ReadAttemptVisitor(Proxmark3Device proxmark3Device,
                    ShouldContinueCallback shouldContinueCallback) {
                this.proxmark3Device = proxmark3Device;
                this.shouldContinueCallback = shouldContinueCallback;
            }

            @Override
            public Map<MifareCardData.SectorNumber, MifareCardData.Sector> visit(
                    StaticKeyMifareReadAttempt staticKeyMifareReadAttempt) throws IOException {
                final Map<MifareCardData.SectorNumber, MifareCardData.Sector> sectors =
                        new HashMap<>();

                for (final MifareCardData.SectorNumber sectorNumber :
                        staticKeyMifareReadAttempt.sectorNumbers) {
                    if (!shouldContinueCallback.shouldContinue()) {
                        break;
                    }

                    for (MifareCardData.KeySlot keySlot :
                            staticKeyMifareReadAttempt.keySlot.getKeySlots()) {
                        if (!shouldContinueCallback.shouldContinue()) {
                            break;
                        }

                        boolean didRead = proxmark3Device.sendThenReceiveCommands(
                                new Proxmark3Command(Proxmark3Command.MIFARE_READSC,
                                        new long[]{sectorNumber.number,
                                                keySlot == MifareCardData.KeySlot.A ? 0 : 1, 0},
                                        staticKeyMifareReadAttempt.key.key),
                                new ReceiveSink<Proxmark3Command, Boolean>() {
                                    @Override
                                    public Boolean onReceived(Proxmark3Command in) {
                                        if (in.op != Proxmark3Command.ACK) {
                                            return null;
                                        }

                                        if ((in.args[0] & 0xff) == 0) {
                                            return false;
                                        }

                                        // TODO: check the way the sector size is determined
                                        // TODO: put determination into reusable form
                                        sectors.put(sectorNumber, new MifareCardData.Sector(
                                                ArrayUtils.subarray(in.data, 0,
                                                        (sectorNumber.number < 32 ? 4 : 16) * 16)));

                                        return true;
                                    }

                                    @Override
                                    public boolean wantsMore() {
                                        return shouldContinueCallback.shouldContinue();
                                    }
                                });

                        if (didRead) {
                            break;
                        }
                    }
                }

                return sectors;
            }
        }
    }

    private static class CommandWaiter
            extends WatchdogReceiveSink<Proxmark3Command, Proxmark3Command> {

        private final long op;

        CommandWaiter(long op, @SuppressWarnings("SameParameterValue") long timeout) {
            super(timeout);

            this.op = op;
        }

        @Override
        public Proxmark3Command onReceived(Proxmark3Command in) {
            return in.op == op ? in : null;
        }
    }

    @Parcel
    public static class TuneResult {

        public final boolean lf;
        public final boolean hf;

        public final float[] lfVoltages;
        public final Float v125;
        public final Float v134;
        public final Float peakF;
        public final Float peakV;
        public final Float hfVoltage;

        @ParcelConstructor
        TuneResult(boolean lf, boolean hf, float[] lfVoltages, Float v125, Float v134, Float peakF,
                Float peakV, Float hfVoltage) {
            this.lf = lf;
            this.hf = hf;

            this.lfVoltages = lfVoltages;
            this.v125 = v125;
            this.v134 = v134;
            this.peakF = peakF;
            this.peakV = peakV;

            this.hfVoltage = hfVoltage;
        }
    }
}
