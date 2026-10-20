package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.decode;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.common.biari.MDecoder;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.H264Utils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.decode.aso.MapManager;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.decode.aso.Mapper;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.CABAC;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.CAVLC;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.model.NALUnit;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.model.NALUnitType;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.model.PictureParameterSet;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.model.SeqParameterSet;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.model.SliceHeader;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.IntObjectMap;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.BitReader;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.logging.Logger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class FrameReader {
   private IntObjectMap<SeqParameterSet> sps = new IntObjectMap<>();
   private IntObjectMap<PictureParameterSet> pps = new IntObjectMap<>();

   public List<SliceReader> readFrame(List<ByteBuffer> nalUnits) {
      List<SliceReader> result = new ArrayList<>();

      for (ByteBuffer nalData : nalUnits) {
         NALUnit nalUnit = NALUnit.read(nalData);
         H264Utils.unescapeNAL(nalData);
         if (NALUnitType.SPS == nalUnit.type) {
            SeqParameterSet _sps = SeqParameterSet.read(nalData);
            this.sps.put(_sps.seqParameterSetId, _sps);
         } else if (NALUnitType.PPS == nalUnit.type) {
            PictureParameterSet _pps = PictureParameterSet.read(nalData);
            this.pps.put(_pps.picParameterSetId, _pps);
         } else if (NALUnitType.IDR_SLICE == nalUnit.type || NALUnitType.NON_IDR_SLICE == nalUnit.type) {
            if (this.sps.size() == 0 || this.pps.size() == 0) {
               Logger.warn("Skipping frame as no SPS/PPS have been seen so far...");
               return null;
            }

            result.add(this.createSliceReader(nalData, nalUnit));
         }
      }

      return result;
   }

   private SliceReader createSliceReader(ByteBuffer segment, NALUnit nalUnit) {
      BitReader _in = BitReader.createBitReader(segment);
      SliceHeader sh = SliceHeaderReader.readPart1(_in);
      sh.pps = this.pps.get(sh.picParameterSetId);
      sh.sps = this.sps.get(sh.pps.seqParameterSetId);
      SliceHeaderReader.readPart2(sh, nalUnit, sh.sps, sh.pps, _in);
      Mapper mapper = new MapManager(sh.sps, sh.pps).getMapper(sh);
      CAVLC[] cavlc = new CAVLC[]{new CAVLC(sh.sps, sh.pps, 2, 2), new CAVLC(sh.sps, sh.pps, 1, 1), new CAVLC(sh.sps, sh.pps, 1, 1)};
      int mbWidth = sh.sps.picWidthInMbsMinus1 + 1;
      CABAC cabac = new CABAC(mbWidth);
      MDecoder mDecoder = null;
      if (sh.pps.entropyCodingModeFlag) {
         _in.terminate();
         int[][] cm = new int[2][1024];
         int qp = sh.pps.picInitQpMinus26 + 26 + sh.sliceQpDelta;
         cabac.initModels(cm, sh.sliceType, sh.cabacInitIdc, qp);
         mDecoder = new MDecoder(segment, cm);
      }

      return new SliceReader(sh.pps, cabac, cavlc, mDecoder, _in, mapper, sh, nalUnit);
   }

   public void addSpsList(List<ByteBuffer> spsList) {
      for (ByteBuffer byteBuffer : spsList) {
         this.addSps(byteBuffer);
      }
   }

   public void addSps(ByteBuffer byteBuffer) {
      ByteBuffer clone = NIOUtils.clone(byteBuffer);
      H264Utils.unescapeNAL(clone);
      SeqParameterSet s = SeqParameterSet.read(clone);
      this.sps.put(s.seqParameterSetId, s);
   }

   public void addPpsList(List<ByteBuffer> ppsList) {
      for (ByteBuffer byteBuffer : ppsList) {
         this.addPps(byteBuffer);
      }
   }

   public void addPps(ByteBuffer byteBuffer) {
      ByteBuffer clone = NIOUtils.clone(byteBuffer);
      H264Utils.unescapeNAL(clone);
      PictureParameterSet p = PictureParameterSet.read(clone);
      this.pps.put(p.picParameterSetId, p);
   }
}
