package io.github.simo255.onnvolumebridge;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.EnumMap;

/** Entirely local QR generation: no shortening, upload or network service. */
final class ReportQr {
    static BitMatrix encode(String url) throws Exception {
        if(url==null || !(url.startsWith(ProblemReport.ISSUES+"?") || url.startsWith("## Problem\n")) || url.length()>2400)
            throw new IllegalArgumentException("Invalid report");
        EnumMap<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN,4);
        hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M);
        return new QRCodeWriter().encode(url,com.google.zxing.BarcodeFormat.QR_CODE,0,0,hints);
    }
}
