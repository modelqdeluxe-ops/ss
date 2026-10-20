package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.mpeg4;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.BitReader;
import java.nio.ByteBuffer;

public class MPEG4DecodingContext {
   public int width;
   public int height;
   public int horiz_mc_ref;
   public int vert_mc_ref;
   public short[] intraMpegQuantMatrix;
   public short[] interMpegQuantMatrix;
   public int[][] gmcWarps;
   public int mbWidth;
   public int mbHeight;
   public int spriteEnable;
   public int shape;
   public int quant;
   public int quantBits;
   public int timeIncrementBits;
   public int intraDCThreshold;
   public int spriteWarpingPoints;
   public boolean reducedResolutionEnable;
   public int fcodeForward;
   public int fcodeBackward;
   public boolean newPredEnable;
   public boolean rounding;
   public boolean quarterPel;
   public boolean cartoonMode;
   public int lastTimeBase;
   public int timeBase;
   public int time;
   public int lastNonBTime;
   public int pframeTs;
   public int bframeTs;
   public boolean topFieldFirst;
   public boolean alternateVerticalScan;
   int volVersionId;
   int timestampMSB;
   int timestampLSB;
   boolean complexityEstimationDisable;
   boolean interlacing;
   boolean spriteBrightnessChange;
   boolean scalability;
   MPEG4DecodingContext.Estimation estimation;
   private static final int VIDOBJ_START_CODE = 256;
   private static final int VIDOBJLAY_START_CODE = 288;
   private static final int VISOBJSEQ_START_CODE = 432;
   private static final int VISOBJSEQ_STOP_CODE = 433;
   private static final int USERDATA_START_CODE = 434;
   private static final int GRPOFVOP_START_CODE = 435;
   private static final int VISOBJ_START_CODE = 437;
   private static final int VISOBJ_TYPE_VIDEO = 1;
   private static final int VIDOBJLAY_AR_EXTPAR = 15;
   private static final int VIDOBJLAY_SHAPE_RECTANGULAR = 0;
   private static final int VIDOBJLAY_SHAPE_BINARY = 1;
   private static final int VIDOBJLAY_SHAPE_BINARY_ONLY = 2;
   private static final int VIDOBJLAY_SHAPE_GRAYSCALE = 3;
   private static final int VOP_START_CODE = 438;
   private static final int VIDOBJ_START_CODE_MASK = 31;
   private static final int VIDOBJLAY_START_CODE_MASK = 15;
   private static final int SPRITE_STATIC = 1;
   private static final int SPRITE_GMC = 2;
   private static final int VLC_CODE = 0;
   private static final int VLC_LEN = 1;
   private int timeIncrementResolution;
   private boolean packedMode;
   public int codingType;
   public boolean quantType;
   public int bsVersion = 65535;

   public MPEG4DecodingContext() {
      this.intraMpegQuantMatrix = new short[64];
      this.interMpegQuantMatrix = new short[64];
      this.gmcWarps = new int[3][2];
      this.estimation = new MPEG4DecodingContext.Estimation();
   }

   public static MPEG4DecodingContext readFromHeaders(ByteBuffer bb) {
      MPEG4DecodingContext ret = new MPEG4DecodingContext();
      return ret.readHeaders(bb) ? ret : null;
   }

   public static void getMatrix(BitReader br, short[] matrix) {
      int value = 0;
      int i = 0;

      int last;
      do {
         last = value;
         value = br.readNBit(8);
         matrix[MPEG4Consts.SCAN_TABLES[0][i++]] = (short)value;
      } while (value != 0 && i < 64);

      i--;

      while (i < 64) {
         matrix[MPEG4Consts.SCAN_TABLES[0][i++]] = (short)last;
      }
   }

   public boolean readHeaders(ByteBuffer param1) {
      // $VF: Couldn't be decompiled
      // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
      // java.lang.OutOfMemoryError: Java heap space
      //   at org.jetbrains.java.decompiler.util.collections.FastSparseSetFactory$FastSparseSet.getCopy(FastSparseSetFactory.java:95)
      //   at org.jetbrains.java.decompiler.util.collections.SFormsFastMapDirect.getCopy(SFormsFastMapDirect.java:67)
      //   at org.jetbrains.java.decompiler.modules.decompiler.sforms.SFormsConstructor.mergeInVarMaps(SFormsConstructor.java:243)
      //   at org.jetbrains.java.decompiler.modules.decompiler.sforms.SFormsConstructor.ssaStatements(SFormsConstructor.java:107)
      //   at org.jetbrains.java.decompiler.modules.decompiler.sforms.SFormsConstructor.splitVariables(SFormsConstructor.java:94)
      //   at org.jetbrains.java.decompiler.modules.decompiler.sforms.SSAUConstructorSparseEx.splitVariables(SSAUConstructorSparseEx.java:43)
      //   at org.jetbrains.java.decompiler.modules.decompiler.StackVarsProcessor.simplifyStackVars(StackVarsProcessor.java:86)
      //   at org.jetbrains.java.decompiler.modules.decompiler.StackVarsProcessor.simplifyStackVars(StackVarsProcessor.java:40)
      //   at org.jetbrains.java.decompiler.main.rels.MethodProcessor.codeToJava(MethodProcessor.java:292)
      //
      // Bytecode:
      // 000: aload 1
      // 001: getstatic java/nio/ByteOrder.BIG_ENDIAN Ljava/nio/ByteOrder;
      // 004: invokevirtual java/nio/ByteBuffer.order (Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;
      // 007: pop
      // 008: aload 1
      // 009: invokevirtual java/nio/ByteBuffer.remaining ()I
      // 00c: bipush 4
      // 00d: if_icmplt 5fb
      // 010: aload 1
      // 011: invokevirtual java/nio/ByteBuffer.getInt ()I
      // 014: istore 2
      // 015: iload 2
      // 016: sipush -256
      // 019: iand
      // 01a: sipush 256
      // 01d: if_icmpeq 03a
      // 020: aload 1
      // 021: invokevirtual java/nio/ByteBuffer.hasRemaining ()Z
      // 024: ifeq 03a
      // 027: iload 2
      // 028: bipush 8
      // 02a: ishl
      // 02b: istore 2
      // 02c: iload 2
      // 02d: aload 1
      // 02e: invokevirtual java/nio/ByteBuffer.get ()B
      // 031: sipush 255
      // 034: iand
      // 035: ior
      // 036: istore 2
      // 037: goto 015
      // 03a: iload 2
      // 03b: sipush 432
      // 03e: if_icmpne 049
      // 041: aload 1
      // 042: invokevirtual java/nio/ByteBuffer.get ()B
      // 045: istore 3
      // 046: goto 5f8
      // 049: iload 2
      // 04a: sipush 433
      // 04d: if_icmpne 053
      // 050: goto 5f8
      // 053: iload 2
      // 054: sipush 437
      // 057: if_icmpne 0c9
      // 05a: aload 1
      // 05b: invokestatic com/fscrates/libs/org/jcodec/common/io/BitReader.createBitReader (Ljava/nio/ByteBuffer;)Lcom/fscrates/libs/org/jcodec/common/io/BitReader;
      // 05e: astore 4
      // 060: aload 4
      // 062: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 065: ifeq 079
      // 068: aload 4
      // 06a: bipush 4
      // 06b: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 06e: istore 3
      // 06f: aload 4
      // 071: bipush 3
      // 072: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 075: pop
      // 076: goto 07b
      // 079: bipush 1
      // 07a: istore 3
      // 07b: aload 4
      // 07d: bipush 4
      // 07e: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 081: istore 5
      // 083: iload 5
      // 085: bipush 1
      // 086: if_icmpeq 08b
      // 089: bipush 0
      // 08a: ireturn
      // 08b: aload 4
      // 08d: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 090: ifeq 0c1
      // 093: aload 4
      // 095: bipush 3
      // 096: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 099: pop
      // 09a: aload 4
      // 09c: bipush 1
      // 09d: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 0a0: pop
      // 0a1: aload 4
      // 0a3: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 0a6: ifeq 0c1
      // 0a9: aload 4
      // 0ab: bipush 8
      // 0ad: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 0b0: pop
      // 0b1: aload 4
      // 0b3: bipush 8
      // 0b5: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 0b8: pop
      // 0b9: aload 4
      // 0bb: bipush 8
      // 0bd: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 0c0: pop
      // 0c1: aload 4
      // 0c3: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.terminate ()V
      // 0c6: goto 5f8
      // 0c9: iload 2
      // 0ca: bipush -32
      // 0cc: iand
      // 0cd: sipush 256
      // 0d0: if_icmpne 0d6
      // 0d3: goto 5f8
      // 0d6: iload 2
      // 0d7: bipush -16
      // 0d9: iand
      // 0da: sipush 288
      // 0dd: if_icmpne 4b9
      // 0e0: aload 1
      // 0e1: invokestatic com/fscrates/libs/org/jcodec/common/io/BitReader.createBitReader (Ljava/nio/ByteBuffer;)Lcom/fscrates/libs/org/jcodec/common/io/BitReader;
      // 0e4: astore 3
      // 0e5: aload 3
      // 0e6: bipush 1
      // 0e7: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 0ea: pop
      // 0eb: aload 3
      // 0ec: bipush 8
      // 0ee: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 0f1: pop
      // 0f2: aload 3
      // 0f3: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 0f6: ifeq 10b
      // 0f9: aload 0
      // 0fa: aload 3
      // 0fb: bipush 4
      // 0fc: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 0ff: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.volVersionId I
      // 102: aload 3
      // 103: bipush 3
      // 104: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 107: pop
      // 108: goto 110
      // 10b: aload 0
      // 10c: bipush 1
      // 10d: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.volVersionId I
      // 110: aload 3
      // 111: bipush 4
      // 112: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 115: istore 4
      // 117: iload 4
      // 119: bipush 15
      // 11b: if_icmpne 12c
      // 11e: aload 3
      // 11f: bipush 8
      // 121: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 124: pop
      // 125: aload 3
      // 126: bipush 8
      // 128: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 12b: pop
      // 12c: aload 3
      // 12d: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 130: ifeq 1a4
      // 133: aload 3
      // 134: bipush 2
      // 135: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 138: pop
      // 139: aload 3
      // 13a: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 13d: istore 5
      // 13f: aload 3
      // 140: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 143: ifeq 1a4
      // 146: aload 3
      // 147: bipush 15
      // 149: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 14c: bipush 15
      // 14e: ishl
      // 14f: istore 6
      // 151: aload 3
      // 152: bipush 1
      // 153: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 156: pop
      // 157: iload 6
      // 159: aload 3
      // 15a: bipush 15
      // 15c: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 15f: ior
      // 160: istore 6
      // 162: aload 3
      // 163: bipush 1
      // 164: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 167: pop
      // 168: aload 3
      // 169: bipush 15
      // 16b: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 16e: bipush 3
      // 16f: ishl
      // 170: istore 7
      // 172: aload 3
      // 173: bipush 1
      // 174: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 177: pop
      // 178: iload 7
      // 17a: aload 3
      // 17b: bipush 3
      // 17c: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 17f: ior
      // 180: istore 7
      // 182: aload 3
      // 183: bipush 11
      // 185: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 188: bipush 15
      // 18a: ishl
      // 18b: istore 8
      // 18d: aload 3
      // 18e: bipush 1
      // 18f: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 192: pop
      // 193: iload 8
      // 195: aload 3
      // 196: bipush 15
      // 198: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 19b: ior
      // 19c: istore 8
      // 19e: aload 3
      // 19f: bipush 1
      // 1a0: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 1a3: pop
      // 1a4: aload 0
      // 1a5: aload 3
      // 1a6: bipush 2
      // 1a7: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 1aa: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.shape I
      // 1ad: aload 0
      // 1ae: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.shape I
      // 1b1: ifeq 1b4
      // 1b4: aload 0
      // 1b5: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.shape I
      // 1b8: bipush 3
      // 1b9: if_icmpne 1ca
      // 1bc: aload 0
      // 1bd: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.volVersionId I
      // 1c0: bipush 1
      // 1c1: if_icmpeq 1ca
      // 1c4: aload 3
      // 1c5: bipush 4
      // 1c6: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 1c9: pop
      // 1ca: aload 3
      // 1cb: bipush 1
      // 1cc: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 1cf: pop
      // 1d0: aload 0
      // 1d1: aload 3
      // 1d2: bipush 16
      // 1d4: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 1d7: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.timeIncrementResolution I
      // 1da: aload 0
      // 1db: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.timeIncrementResolution I
      // 1de: ifle 1f7
      // 1e1: aload 0
      // 1e2: aload 0
      // 1e3: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.timeIncrementResolution I
      // 1e6: bipush 1
      // 1e7: isub
      // 1e8: invokestatic com/fscrates/libs/org/jcodec/common/tools/MathUtil.log2 (I)I
      // 1eb: bipush 1
      // 1ec: iadd
      // 1ed: bipush 1
      // 1ee: invokestatic java/lang/Math.max (II)I
      // 1f1: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.timeIncrementBits I
      // 1f4: goto 1fc
      // 1f7: aload 0
      // 1f8: bipush 1
      // 1f9: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.timeIncrementBits I
      // 1fc: aload 3
      // 1fd: bipush 1
      // 1fe: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 201: pop
      // 202: aload 3
      // 203: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 206: ifeq 212
      // 209: aload 3
      // 20a: aload 0
      // 20b: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.timeIncrementBits I
      // 20e: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 211: pop
      // 212: aload 0
      // 213: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.shape I
      // 216: bipush 2
      // 217: if_icmpeq 475
      // 21a: aload 0
      // 21b: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.shape I
      // 21e: ifne 24b
      // 221: aload 3
      // 222: bipush 1
      // 223: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 226: pop
      // 227: aload 0
      // 228: aload 3
      // 229: bipush 13
      // 22b: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 22e: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.width I
      // 231: aload 3
      // 232: bipush 1
      // 233: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 236: pop
      // 237: aload 0
      // 238: aload 3
      // 239: bipush 13
      // 23b: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 23e: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.height I
      // 241: aload 3
      // 242: bipush 1
      // 243: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 246: pop
      // 247: aload 0
      // 248: invokespecial com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.calcSizes ()V
      // 24b: aload 0
      // 24c: aload 3
      // 24d: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 250: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.interlacing Z
      // 253: aload 3
      // 254: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 257: ifne 25a
      // 25a: aload 0
      // 25b: aload 3
      // 25c: aload 0
      // 25d: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.volVersionId I
      // 260: bipush 1
      // 261: if_icmpne 268
      // 264: bipush 1
      // 265: goto 269
      // 268: bipush 2
      // 269: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 26c: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.spriteEnable I
      // 26f: aload 0
      // 270: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.spriteEnable I
      // 273: bipush 1
      // 274: if_icmpeq 27f
      // 277: aload 0
      // 278: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.spriteEnable I
      // 27b: bipush 2
      // 27c: if_icmpne 2e1
      // 27f: aload 0
      // 280: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.spriteEnable I
      // 283: bipush 2
      // 284: if_icmpeq 2bb
      // 287: aload 3
      // 288: bipush 13
      // 28a: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 28d: pop
      // 28e: aload 3
      // 28f: bipush 1
      // 290: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 293: pop
      // 294: aload 3
      // 295: bipush 13
      // 297: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 29a: pop
      // 29b: aload 3
      // 29c: bipush 1
      // 29d: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 2a0: pop
      // 2a1: aload 3
      // 2a2: bipush 13
      // 2a4: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 2a7: pop
      // 2a8: aload 3
      // 2a9: bipush 1
      // 2aa: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 2ad: pop
      // 2ae: aload 3
      // 2af: bipush 13
      // 2b1: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 2b4: pop
      // 2b5: aload 3
      // 2b6: bipush 1
      // 2b7: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 2ba: pop
      // 2bb: aload 0
      // 2bc: aload 3
      // 2bd: bipush 6
      // 2bf: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 2c2: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.spriteWarpingPoints I
      // 2c5: aload 3
      // 2c6: bipush 2
      // 2c7: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 2ca: pop
      // 2cb: aload 0
      // 2cc: aload 3
      // 2cd: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 2d0: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.spriteBrightnessChange Z
      // 2d3: aload 0
      // 2d4: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.spriteEnable I
      // 2d7: bipush 2
      // 2d8: if_icmpeq 2e1
      // 2db: aload 3
      // 2dc: bipush 1
      // 2dd: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 2e0: pop
      // 2e1: aload 0
      // 2e2: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.volVersionId I
      // 2e5: bipush 1
      // 2e6: if_icmpeq 2f6
      // 2e9: aload 0
      // 2ea: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.shape I
      // 2ed: ifeq 2f6
      // 2f0: aload 3
      // 2f1: bipush 1
      // 2f2: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 2f5: pop
      // 2f6: aload 3
      // 2f7: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 2fa: ifeq 30f
      // 2fd: aload 0
      // 2fe: aload 3
      // 2ff: bipush 4
      // 300: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 303: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.quantBits I
      // 306: aload 3
      // 307: bipush 4
      // 308: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 30b: pop
      // 30c: goto 314
      // 30f: aload 0
      // 310: bipush 5
      // 311: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.quantBits I
      // 314: aload 0
      // 315: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.shape I
      // 318: bipush 3
      // 319: if_icmpne 32e
      // 31c: aload 3
      // 31d: bipush 1
      // 31e: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 321: pop
      // 322: aload 3
      // 323: bipush 1
      // 324: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 327: pop
      // 328: aload 3
      // 329: bipush 1
      // 32a: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 32d: pop
      // 32e: aload 0
      // 32f: aload 3
      // 330: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 333: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.quantType Z
      // 336: aload 0
      // 337: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.quantType Z
      // 33a: ifeq 38d
      // 33d: aload 3
      // 33e: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 341: ifeq 34f
      // 344: aload 3
      // 345: aload 0
      // 346: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.intraMpegQuantMatrix [S
      // 349: invokestatic com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.getMatrix (Lcom/fscrates/libs/org/jcodec/common/io/BitReader;[S)V
      // 34c: goto 360
      // 34f: getstatic com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4Consts.DEFAULT_INTRA_MATRIX [S
      // 352: bipush 0
      // 353: aload 0
      // 354: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.intraMpegQuantMatrix [S
      // 357: bipush 0
      // 358: aload 0
      // 359: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.intraMpegQuantMatrix [S
      // 35c: arraylength
      // 35d: invokestatic java/lang/System.arraycopy (Ljava/lang/Object;ILjava/lang/Object;II)V
      // 360: aload 3
      // 361: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 364: ifeq 372
      // 367: aload 3
      // 368: aload 0
      // 369: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.interMpegQuantMatrix [S
      // 36c: invokestatic com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.getMatrix (Lcom/fscrates/libs/org/jcodec/common/io/BitReader;[S)V
      // 36f: goto 383
      // 372: getstatic com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4Consts.DEFAULT_INTER_MATRIX [S
      // 375: bipush 0
      // 376: aload 0
      // 377: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.interMpegQuantMatrix [S
      // 37a: bipush 0
      // 37b: aload 0
      // 37c: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.interMpegQuantMatrix [S
      // 37f: arraylength
      // 380: invokestatic java/lang/System.arraycopy (Ljava/lang/Object;ILjava/lang/Object;II)V
      // 383: aload 0
      // 384: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.shape I
      // 387: bipush 3
      // 388: if_icmpne 38d
      // 38b: bipush 0
      // 38c: ireturn
      // 38d: aload 0
      // 38e: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.volVersionId I
      // 391: bipush 1
      // 392: if_icmpeq 3a0
      // 395: aload 0
      // 396: aload 3
      // 397: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 39a: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.quarterPel Z
      // 39d: goto 3a5
      // 3a0: aload 0
      // 3a1: bipush 0
      // 3a2: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.quarterPel Z
      // 3a5: aload 0
      // 3a6: aload 3
      // 3a7: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 3aa: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.complexityEstimationDisable Z
      // 3ad: aload 0
      // 3ae: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.complexityEstimationDisable Z
      // 3b1: ifne 3bd
      // 3b4: aload 0
      // 3b5: aload 3
      // 3b6: aload 0
      // 3b7: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.estimation Lcom/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext$Estimation;
      // 3ba: invokespecial com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.readVolComplexityEstimationHeader (Lcom/fscrates/libs/org/jcodec/common/io/BitReader;Lcom/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext$Estimation;)V
      // 3bd: aload 3
      // 3be: bipush 1
      // 3bf: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 3c2: pop
      // 3c3: aload 3
      // 3c4: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 3c7: ifeq 3d0
      // 3ca: aload 3
      // 3cb: bipush 1
      // 3cc: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 3cf: pop
      // 3d0: aload 0
      // 3d1: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.volVersionId I
      // 3d4: bipush 1
      // 3d5: if_icmpeq 3fe
      // 3d8: aload 0
      // 3d9: aload 3
      // 3da: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 3dd: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.newPredEnable Z
      // 3e0: aload 0
      // 3e1: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.newPredEnable Z
      // 3e4: ifeq 3f3
      // 3e7: aload 3
      // 3e8: bipush 2
      // 3e9: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 3ec: pop
      // 3ed: aload 3
      // 3ee: bipush 1
      // 3ef: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 3f2: pop
      // 3f3: aload 0
      // 3f4: aload 3
      // 3f5: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 3f8: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.reducedResolutionEnable Z
      // 3fb: goto 408
      // 3fe: aload 0
      // 3ff: bipush 0
      // 400: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.newPredEnable Z
      // 403: aload 0
      // 404: bipush 0
      // 405: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.reducedResolutionEnable Z
      // 408: aload 0
      // 409: aload 3
      // 40a: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 40d: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.scalability Z
      // 410: aload 0
      // 411: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.scalability Z
      // 414: ifeq 4b2
      // 417: aload 3
      // 418: bipush 1
      // 419: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 41c: pop
      // 41d: aload 3
      // 41e: bipush 4
      // 41f: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 422: pop
      // 423: aload 3
      // 424: bipush 1
      // 425: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 428: pop
      // 429: aload 3
      // 42a: bipush 5
      // 42b: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 42e: pop
      // 42f: aload 3
      // 430: bipush 5
      // 431: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 434: pop
      // 435: aload 3
      // 436: bipush 5
      // 437: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 43a: pop
      // 43b: aload 3
      // 43c: bipush 5
      // 43d: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 440: pop
      // 441: aload 3
      // 442: bipush 1
      // 443: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 446: pop
      // 447: aload 0
      // 448: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.shape I
      // 44b: bipush 1
      // 44c: if_icmpne 473
      // 44f: aload 3
      // 450: bipush 1
      // 451: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 454: pop
      // 455: aload 3
      // 456: bipush 1
      // 457: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 45a: pop
      // 45b: aload 3
      // 45c: bipush 5
      // 45d: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 460: pop
      // 461: aload 3
      // 462: bipush 5
      // 463: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 466: pop
      // 467: aload 3
      // 468: bipush 5
      // 469: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 46c: pop
      // 46d: aload 3
      // 46e: bipush 5
      // 46f: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 472: pop
      // 473: bipush 0
      // 474: ireturn
      // 475: aload 0
      // 476: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.volVersionId I
      // 479: bipush 1
      // 47a: if_icmpeq 4ac
      // 47d: aload 0
      // 47e: aload 3
      // 47f: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readBool ()Z
      // 482: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.scalability Z
      // 485: aload 0
      // 486: getfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.scalability Z
      // 489: ifeq 4ac
      // 48c: aload 3
      // 48d: bipush 4
      // 48e: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 491: pop
      // 492: aload 3
      // 493: bipush 5
      // 494: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 497: pop
      // 498: aload 3
      // 499: bipush 5
      // 49a: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 49d: pop
      // 49e: aload 3
      // 49f: bipush 5
      // 4a0: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 4a3: pop
      // 4a4: aload 3
      // 4a5: bipush 5
      // 4a6: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 4a9: pop
      // 4aa: bipush 0
      // 4ab: ireturn
      // 4ac: aload 3
      // 4ad: bipush 1
      // 4ae: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 4b1: pop
      // 4b2: aload 3
      // 4b3: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.terminate ()V
      // 4b6: goto 5f8
      // 4b9: iload 2
      // 4ba: sipush 435
      // 4bd: if_icmpne 4f5
      // 4c0: aload 1
      // 4c1: invokestatic com/fscrates/libs/org/jcodec/common/io/BitReader.createBitReader (Ljava/nio/ByteBuffer;)Lcom/fscrates/libs/org/jcodec/common/io/BitReader;
      // 4c4: astore 3
      // 4c5: aload 3
      // 4c6: bipush 5
      // 4c7: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 4ca: istore 4
      // 4cc: aload 3
      // 4cd: bipush 6
      // 4cf: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 4d2: istore 5
      // 4d4: aload 3
      // 4d5: bipush 1
      // 4d6: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 4d9: pop
      // 4da: aload 3
      // 4db: bipush 6
      // 4dd: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.readNBit (I)I
      // 4e0: istore 6
      // 4e2: aload 3
      // 4e3: bipush 1
      // 4e4: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 4e7: pop
      // 4e8: aload 3
      // 4e9: bipush 1
      // 4ea: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.skip (I)I
      // 4ed: pop
      // 4ee: aload 3
      // 4ef: invokevirtual com/fscrates/libs/org/jcodec/common/io/BitReader.terminate ()V
      // 4f2: goto 5f8
      // 4f5: iload 2
      // 4f6: sipush 438
      // 4f9: if_icmpne 4fe
      // 4fc: bipush 1
      // 4fd: ireturn
      // 4fe: iload 2
      // 4ff: sipush 434
      // 502: if_icmpne 5f2
      // 505: sipush 256
      // 508: newarray 8
      // 50a: astore 3
      // 50b: bipush 0
      // 50c: istore 4
      // 50e: aload 3
      // 50f: iload 4
      // 511: iinc 4 1
      // 514: aload 1
      // 515: invokevirtual java/nio/ByteBuffer.get ()B
      // 518: bastore
      // 519: aload 3
      // 51a: iload 4
      // 51c: aload 1
      // 51d: invokevirtual java/nio/ByteBuffer.get ()B
      // 520: dup_x2
      // 521: bastore
      // 522: ifeq 52b
      // 525: iinc 4 1
      // 528: goto 519
      // 52b: aload 1
      // 52c: aload 1
      // 52d: invokevirtual java/nio/ByteBuffer.position ()I
      // 530: bipush 1
      // 531: isub
      // 532: invokevirtual java/nio/ByteBuffer.position (I)Ljava/nio/Buffer;
      // 535: pop
      // 536: new java/lang/String
      // 539: dup
      // 53a: aload 3
      // 53b: bipush 0
      // 53c: iload 4
      // 53e: invokespecial java/lang/String.<init> ([BII)V
      // 541: astore 5
      // 543: aload 5
      // 545: ldc_w "XviD"
      // 548: invokevirtual java/lang/String.startsWith (Ljava/lang/String;)Z
      // 54b: ifeq 585
      // 54e: aload 3
      // 54f: aload 5
      // 551: invokevirtual java/lang/String.length ()I
      // 554: bipush 1
      // 555: isub
      // 556: baload
      // 557: bipush 67
      // 559: if_icmpne 578
      // 55c: aload 0
      // 55d: aload 5
      // 55f: bipush 4
      // 560: aload 5
      // 562: invokevirtual java/lang/String.length ()I
      // 565: bipush 1
      // 566: isub
      // 567: invokevirtual java/lang/String.substring (II)Ljava/lang/String;
      // 56a: invokestatic java/lang/Integer.parseInt (Ljava/lang/String;)I
      // 56d: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.bsVersion I
      // 570: aload 0
      // 571: bipush 1
      // 572: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.cartoonMode Z
      // 575: goto 585
      // 578: aload 0
      // 579: aload 5
      // 57b: bipush 4
      // 57c: invokevirtual java/lang/String.substring (I)Ljava/lang/String;
      // 57f: invokestatic java/lang/Integer.parseInt (Ljava/lang/String;)I
      // 582: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.bsVersion I
      // 585: aload 5
      // 587: ldc_w "DivX"
      // 58a: invokevirtual java/lang/String.startsWith (Ljava/lang/String;)Z
      // 58d: ifeq 5ef
      // 590: aload 5
      // 592: ldc_w "Build"
      // 595: invokevirtual java/lang/String.indexOf (Ljava/lang/String;)I
      // 598: istore 9
      // 59a: iload 9
      // 59c: bipush -1
      // 59d: if_icmpne 5aa
      // 5a0: aload 5
      // 5a2: ldc_w "b"
      // 5a5: invokevirtual java/lang/String.indexOf (Ljava/lang/String;)I
      // 5a8: istore 9
      // 5aa: aload 5
      // 5ac: bipush 4
      // 5ad: iload 9
      // 5af: invokevirtual java/lang/String.substring (II)Ljava/lang/String;
      // 5b2: invokestatic java/lang/Integer.parseInt (Ljava/lang/String;)I
      // 5b5: istore 6
      // 5b7: aload 5
      // 5b9: iload 9
      // 5bb: bipush 1
      // 5bc: iadd
      // 5bd: aload 5
      // 5bf: invokevirtual java/lang/String.length ()I
      // 5c2: bipush 1
      // 5c3: isub
      // 5c4: invokevirtual java/lang/String.substring (II)Ljava/lang/String;
      // 5c7: invokestatic java/lang/Integer.parseInt (Ljava/lang/String;)I
      // 5ca: istore 7
      // 5cc: aload 5
      // 5ce: aload 5
      // 5d0: invokevirtual java/lang/String.length ()I
      // 5d3: bipush 1
      // 5d4: isub
      // 5d5: invokevirtual java/lang/String.charAt (I)C
      // 5d8: istore 8
      // 5da: aload 0
      // 5db: iload 8
      // 5dd: bipush 112
      // 5df: if_icmpne 5e6
      // 5e2: bipush 1
      // 5e3: goto 5e7
      // 5e6: bipush 0
      // 5e7: putfield com/fscrates/libs/org/jcodec/codecs/mpeg4/MPEG4DecodingContext.packedMode Z
      // 5ea: goto 5ef
      // 5ed: astore 10
      // 5ef: goto 5f8
      // 5f2: ldc_w "Unknown"
      // 5f5: invokestatic com/fscrates/libs/org/jcodec/common/logging/Logger.debug (Ljava/lang/String;)V
      // 5f8: goto 008
      // 5fb: bipush 0
      // 5fc: ireturn
   }

   private void calcSizes() {
      this.mbWidth = (this.width + 15) / 16;
      this.mbHeight = (this.height + 15) / 16;
   }

   private void readVolComplexityEstimationHeader(BitReader br, MPEG4DecodingContext.Estimation estimation) {
      estimation.method = br.readNBit(2);
      if (estimation.method == 0 || estimation.method == 1) {
         if (!br.readBool()) {
            estimation.opaque = br.readBool();
            estimation.transparent = br.readBool();
            estimation.intraCae = br.readBool();
            estimation.interCae = br.readBool();
            estimation.noUpdate = br.readBool();
            estimation.upsampling = br.readBool();
         }

         if (!br.readBool()) {
            estimation.intraBlocks = br.readBool();
            estimation.interBlocks = br.readBool();
            estimation.inter4vBlocks = br.readBool();
            estimation.notCodedBlocks = br.readBool();
         }
      }

      br.skip(1);
      if (!br.readBool()) {
         estimation.dctCoefs = br.readBool();
         estimation.dctLines = br.readBool();
         estimation.vlcSymbols = br.readBool();
         estimation.vlcBits = br.readBool();
      }

      if (!br.readBool()) {
         estimation.apm = br.readBool();
         estimation.npm = br.readBool();
         estimation.interpolateMcQ = br.readBool();
         estimation.forwBackMcQ = br.readBool();
         estimation.halfpel2 = br.readBool();
         estimation.halfpel4 = br.readBool();
      }

      br.skip(1);
      if (estimation.method == 1 && !br.readBool()) {
         estimation.sadct = br.readBool();
         estimation.quarterpel = br.readBool();
      }
   }

   public boolean readVOPHeader(BitReader br) {
      this.rounding = false;
      this.quant = 2;
      this.codingType = br.readNBit(2);

      while (br.readBool()) {
         this.timestampMSB++;
      }

      br.skip(1);
      if (this.getTimeIncrementBits() != 0) {
         this.timestampLSB = br.readNBit(this.getTimeIncrementBits());
      }

      br.skip(1);
      if (!br.readBool()) {
         return false;
      } else {
         if (this.newPredEnable) {
            int vopId = br.readNBit(Math.min(this.getTimeIncrementBits() + 3, 15));
            if (br.readBool()) {
               int i = br.readNBit(Math.min(this.getTimeIncrementBits() + 3, 15));
            }

            br.skip(1);
         }

         if (this.shape != 2 && (this.codingType == 1 || this.codingType == 3 && this.spriteEnable == 2)) {
            this.rounding = br.readBool();
         }

         if (this.reducedResolutionEnable && this.shape == 0 && (this.codingType == 1 || this.codingType == 0) && br.readBool()) {
         }

         if (this.shape != 0) {
            if (this.spriteEnable != 1 || this.codingType != 0) {
               this.width = br.readNBit(13);
               br.skip(1);
               this.height = br.readNBit(13);
               br.skip(1);
               this.horiz_mc_ref = br.readNBit(13);
               br.skip(1);
               this.vert_mc_ref = br.readNBit(13);
               br.skip(1);
               this.calcSizes();
            }

            br.skip(1);
            if (br.readBool()) {
               br.skip(8);
            }
         }

         MPEG4DecodingContext.Estimation estimation = new MPEG4DecodingContext.Estimation();
         if (this.shape != 2) {
            if (!this.complexityEstimationDisable) {
               this.readVopComplexityEstimationHeader(br, estimation, this.spriteEnable, this.codingType);
            }

            this.intraDCThreshold = MPEG4Consts.INTRA_DC_THRESHOLD_TABLE[br.readNBit(3)];
            if (this.interlacing) {
               this.topFieldFirst = br.readBool();
               this.alternateVerticalScan = br.readBool();
            }
         }

         if ((this.spriteEnable == 1 || this.spriteEnable == 2) && this.codingType == 3) {
            for (int i = 0; i < this.spriteWarpingPoints; i++) {
               int x = 0;
               int y = 0;
               int length = this.getSpriteTrajectory(br);
               if (length > 0) {
                  x = br.readNBit(length);
                  if (x >> length - 1 == 0) {
                     x = -(x ^ (1 << length) - 1);
                  }
               }

               br.skip(1);
               length = this.getSpriteTrajectory(br);
               if (length > 0) {
                  y = br.readNBit(length);
                  if (y >> length - 1 == 0) {
                     y = -(y ^ (1 << length) - 1);
                  }
               }

               br.skip(1);
               this.gmcWarps[i][0] = x;
               this.gmcWarps[i][1] = y;
            }

            if (this.spriteBrightnessChange) {
            }

            if (this.spriteEnable == 1) {
            }
         }

         if ((this.quant = br.readNBit(this.quantBits)) < 1) {
            this.quant = 1;
         }

         if (this.codingType != 0) {
            this.fcodeForward = br.readNBit(3);
         }

         if (this.codingType == 2) {
            this.fcodeBackward = br.readNBit(3);
         }

         if (!this.scalability && this.shape != 0 && this.codingType != 0) {
            br.skip(1);
         }

         if (this.codingType != 2) {
            this.lastTimeBase = this.timeBase;
            this.timeBase = this.timeBase + this.timestampMSB;
            this.time = this.timeBase * this.getTimeIncrementResolution() + this.timestampLSB;
            this.pframeTs = this.time - this.lastNonBTime;
            this.lastNonBTime = this.time;
         } else {
            this.time = (this.lastTimeBase + this.timestampMSB) * this.getTimeIncrementResolution() + this.timestampLSB;
            this.bframeTs = this.pframeTs - (this.lastNonBTime - this.time);
         }

         return true;
      }
   }

   private int getSpriteTrajectory(BitReader br) {
      for (int i = 0; i < 12; i++) {
         if (br.checkNBit(MPEG4Consts.SPRITE_TRAJECTORY_LEN[i][1]) == MPEG4Consts.SPRITE_TRAJECTORY_LEN[i][0]) {
            br.skip(MPEG4Consts.SPRITE_TRAJECTORY_LEN[i][1]);
            return i;
         }
      }

      return -1;
   }

   private void readVopComplexityEstimationHeader(BitReader br, MPEG4DecodingContext.Estimation estimation, int spriteEnable, int codingType) {
      if (estimation.method == 0 || estimation.method == 1) {
         if (codingType == 0) {
            if (estimation.opaque) {
               br.skip(8);
            }

            if (estimation.transparent) {
               br.skip(8);
            }

            if (estimation.intraCae) {
               br.skip(8);
            }

            if (estimation.interCae) {
               br.skip(8);
            }

            if (estimation.noUpdate) {
               br.skip(8);
            }

            if (estimation.upsampling) {
               br.skip(8);
            }

            if (estimation.intraBlocks) {
               br.skip(8);
            }

            if (estimation.notCodedBlocks) {
               br.skip(8);
            }

            if (estimation.dctCoefs) {
               br.skip(8);
            }

            if (estimation.dctLines) {
               br.skip(8);
            }

            if (estimation.vlcSymbols) {
               br.skip(8);
            }

            if (estimation.vlcBits) {
               br.skip(8);
            }

            if (estimation.sadct) {
               br.skip(8);
            }
         }

         if (codingType == 1) {
            if (estimation.opaque) {
               br.skip(8);
            }

            if (estimation.transparent) {
               br.skip(8);
            }

            if (estimation.intraCae) {
               br.skip(8);
            }

            if (estimation.interCae) {
               br.skip(8);
            }

            if (estimation.noUpdate) {
               br.skip(8);
            }

            if (estimation.upsampling) {
               br.skip(8);
            }

            if (estimation.intraBlocks) {
               br.skip(8);
            }

            if (estimation.notCodedBlocks) {
               br.skip(8);
            }

            if (estimation.dctCoefs) {
               br.skip(8);
            }

            if (estimation.dctLines) {
               br.skip(8);
            }

            if (estimation.vlcSymbols) {
               br.skip(8);
            }

            if (estimation.vlcBits) {
               br.skip(8);
            }

            if (estimation.interBlocks) {
               br.skip(8);
            }

            if (estimation.inter4vBlocks) {
               br.skip(8);
            }

            if (estimation.apm) {
               br.skip(8);
            }

            if (estimation.npm) {
               br.skip(8);
            }

            if (estimation.forwBackMcQ) {
               br.skip(8);
            }

            if (estimation.halfpel2) {
               br.skip(8);
            }

            if (estimation.halfpel4) {
               br.skip(8);
            }

            if (estimation.sadct) {
               br.skip(8);
            }

            if (estimation.quarterpel) {
               br.skip(8);
            }
         }

         if (codingType == 2) {
            if (estimation.opaque) {
               br.skip(8);
            }

            if (estimation.transparent) {
               br.skip(8);
            }

            if (estimation.intraCae) {
               br.skip(8);
            }

            if (estimation.interCae) {
               br.skip(8);
            }

            if (estimation.noUpdate) {
               br.skip(8);
            }

            if (estimation.upsampling) {
               br.skip(8);
            }

            if (estimation.intraBlocks) {
               br.skip(8);
            }

            if (estimation.notCodedBlocks) {
               br.skip(8);
            }

            if (estimation.dctCoefs) {
               br.skip(8);
            }

            if (estimation.dctLines) {
               br.skip(8);
            }

            if (estimation.vlcSymbols) {
               br.skip(8);
            }

            if (estimation.vlcBits) {
               br.skip(8);
            }

            if (estimation.interBlocks) {
               br.skip(8);
            }

            if (estimation.inter4vBlocks) {
               br.skip(8);
            }

            if (estimation.apm) {
               br.skip(8);
            }

            if (estimation.npm) {
               br.skip(8);
            }

            if (estimation.forwBackMcQ) {
               br.skip(8);
            }

            if (estimation.halfpel2) {
               br.skip(8);
            }

            if (estimation.halfpel4) {
               br.skip(8);
            }

            if (estimation.interpolateMcQ) {
               br.skip(8);
            }

            if (estimation.sadct) {
               br.skip(8);
            }

            if (estimation.quarterpel) {
               br.skip(8);
            }
         }

         if (codingType == 3 && spriteEnable == 1) {
            if (estimation.intraBlocks) {
               br.skip(8);
            }

            if (estimation.notCodedBlocks) {
               br.skip(8);
            }

            if (estimation.dctCoefs) {
               br.skip(8);
            }

            if (estimation.dctLines) {
               br.skip(8);
            }

            if (estimation.vlcSymbols) {
               br.skip(8);
            }

            if (estimation.vlcBits) {
               br.skip(8);
            }

            if (estimation.interBlocks) {
               br.skip(8);
            }

            if (estimation.inter4vBlocks) {
               br.skip(8);
            }

            if (estimation.apm) {
               br.skip(8);
            }

            if (estimation.npm) {
               br.skip(8);
            }

            if (estimation.forwBackMcQ) {
               br.skip(8);
            }

            if (estimation.halfpel2) {
               br.skip(8);
            }

            if (estimation.halfpel4) {
               br.skip(8);
            }

            if (estimation.interpolateMcQ) {
               br.skip(8);
            }
         }
      }
   }

   public boolean getPackedMode() {
      return this.packedMode;
   }

   public int getTimeIncrementBits() {
      return this.timeIncrementBits;
   }

   public int getTimeIncrementResolution() {
      return this.timeIncrementResolution;
   }

   private static class Estimation {
      public int method;
      public boolean opaque;
      public boolean transparent;
      public boolean intraCae;
      public boolean interCae;
      public boolean noUpdate;
      public boolean upsampling;
      public boolean intraBlocks;
      public boolean interBlocks;
      public boolean inter4vBlocks;
      public boolean notCodedBlocks;
      public boolean dctCoefs;
      public boolean dctLines;
      public boolean vlcSymbols;
      public boolean vlcBits;
      public boolean apm;
      public boolean npm;
      public boolean interpolateMcQ;
      public boolean forwBackMcQ;
      public boolean halfpel2;
      public boolean halfpel4;
      public boolean sadct;
      public boolean quarterpel;

      private Estimation() {
      }
   }
}
