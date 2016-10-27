/**
 * Scifio-UView plugin. This plugin reads single images from the UKSOFT2000 format. This format is used by the 
 * Elmitec camera adquisition program for their LEEM/PEEM line of instruments.
 * 
 * It is a simple unsigned 16bit binary dump preceeded by a header with some experimental parameters and the size.
 * For the record, it is the same format originally used in a Transputer electronics control unit for Scanning Tunneling
 * Microcopy, from Uwe Knipping (who then moved to Elmitec).
 *
 * @author Juan de la Figuera
 */


import io.scif.AbstractChecker;
import io.scif.AbstractFormat;
import io.scif.AbstractMetadata;
import io.scif.AbstractParser;
import io.scif.AbstractTranslator;
import io.scif.AbstractWriter;
import io.scif.ByteArrayPlane;
import io.scif.ByteArrayReader;
import io.scif.Field;
import io.scif.Format;
import io.scif.FormatException;
import io.scif.HasColorTable;
import io.scif.ImageMetadata;
import io.scif.Plane;
import io.scif.SCIFIO;
import io.scif.config.SCIFIOConfig;
import io.scif.formats.ImageIOFormat.Metadata;
import io.scif.io.RandomAccessInputStream;
import io.scif.io.RandomAccessOutputStream;
import io.scif.services.FormatService;
import io.scif.util.FormatTools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.img.basictypeaccess.array.ShortArray;
import net.imglib2.img.planar.PlanarImg;

import net.imagej.axis.Axes;

import org.scijava.plugin.Plugin;



public class UView_reader {
	
	@Plugin(type = Format.class)
	
	public static class UKFormat extends AbstractFormat {

		public static final String UVIEW_MAGIC_STRING = "UKSOFT";
		
		@Override
		public String getFormatName() {
			return "UKSOFT2000/UView format";
		}

		@Override
		protected String[] makeSuffixArray() {
			return new String[] { "dat" };
		}

		public static class Metadata extends AbstractMetadata {
			
			@Field(label="StartVoltage")
			private float StartVoltage;
			
			@Field(label="offset")
			private int offset;
			
			public float getStartVoltage() {
				return StartVoltage;
			}
			
			public void setStartVoltage(float StartVoltage) {
				this.StartVoltage=StartVoltage;
			}
			
			public int getOffset() {
				return offset;
			}
			
			public void setOffset(int offset) {
				this.offset=offset;
			}
			
			@Override
			public void populateImageMetadata() {

				final ImageMetadata iMeta = get(0);
	
				iMeta.setOrderCertain(true);
				iMeta.setFalseColor(false);
				iMeta.setThumbnail(false);
				iMeta.setPixelType(FormatTools.UINT16);
				iMeta.setLittleEndian(true);
			}
		}

		public static class Parser extends AbstractParser<Metadata> {
			@Override
			public void typedParse(final RandomAccessInputStream stream,
				final Metadata meta, final SCIFIOConfig config) throws IOException,
				FormatException
			{	
				meta.createImageMetadata(1);	
				final ImageMetadata iMeta = meta.get(0);
				stream.order(true); // ByteOrder.LITTLE_ENDIAN
				stream.seek(40);
                int width = stream.readUnsignedShort();
                int height= stream.readUnsignedShort();
    			iMeta.addAxis(Axes.X, width);
    			iMeta.addAxis(Axes.Y, height);
    			
    			meta.setOffset(392); // 392 without overlay, 420 with it.
    				
				config.parserGetLevel();
				config.parserIsFiltered();
				config.parserIsSaveOriginalMetadata();
			}
		}

		public static class Checker extends AbstractChecker {

			@Override
			public boolean suffixSufficient() {
				return false;
			}

			@Override
			public boolean suffixNecessary() {
				return true;
			}

			@Override
			public boolean isFormat(final RandomAccessInputStream in)
					throws IOException
				{
					final int blockLen = UVIEW_MAGIC_STRING.length();
					if (!FormatTools.validStream(in, blockLen, false)) return false;
					return in.readString(blockLen).startsWith(UVIEW_MAGIC_STRING);
				}
			
		public static class Reader extends ByteArrayReader<Metadata> {
			@Override
			public ByteArrayPlane openPlane(int imageIndex, long planeIndex,
				ByteArrayPlane plane, long[] planeMin, long[] planeMax,
				SCIFIOConfig config) throws FormatException, IOException
			{
				final Metadata meta = getMetadata();
				final byte[] buf = plane.getData();
				
				FormatTools.checkPlaneForReading(meta, imageIndex, planeIndex,
						buf.length, planeMin, planeMax);
				
				getStream().seek(meta.getOffset());
				getStream().read(buf);

				if (meta.getClass().isAssignableFrom(HasColorTable.class)) {
					plane.setColorTable(((HasColorTable) meta).getColorTable(imageIndex,
						planeIndex));
				}

				return plane;
			}

			@Override
			protected String[] createDomainArray() {
				return new String[0];
			}

		}

		}
	// This method is provided simply to confirm the format is discovered
	public static void main(final String... args) throws FormatException {
		
		final SCIFIO scifio = new SCIFIO();
		final String sampleImage = "notAnImage.dat";
		final Format format = scifio.format().getFormat(sampleImage);
		System.out.println("UKSoft found via FormatService: " +
			(format != null));

		scifio.getContext().dispose();
	}

}
	
}