/*
 * #%L
 * SCIFIO tutorials for core and plugin use.
 * %%
 * Copyright (C) 2011 - 2016 Open Microscopy Environment:
 * 	- Board of Regents of the University of Wisconsin-Madison
 * 	- Glencoe Software, Inc.
 * 	- University of Dundee
 * %%
 * To the extent possible under law, the SCIFIO developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 * 
 * See the CC0 1.0 Universal license for details:
 * http://creativecommons.org/publicdomain/zero/1.0/
 * #L%
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

				// update the data by reference. Ideally, this limits memory problems 
				// from rapid Java array construction/destruction.
								
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

		// *** OPTIONAL COMPONENTS ***

		// Writers are not implemented for proprietary formats, as doing so
		// typically violates licensing. However, if your format is open source you
		// are welcome to implement a writer.
		public static class Writer extends AbstractWriter<Metadata> {

			// NB: note that there is no writePlane method that uses a SCIFIOConfig.
			// The writer configuration comes into play in the setDest methods.
			// Note that all the default SCIFIOConfig#writer[XXXX] functionality is
			// handled in the Abstract layer.
			// But if there is configuration state for the writer you need to access,
			// you should override this setDest signature (as it is the lowest-level
			// signature, thus guaranteed to be called). Typically you will still want
			// a super.setDest call to ensure the standard boilerplate is handled
			// properly.
//			@Override
//			public void setDest(final RandomAccessOutputStream out, final int imageIndex,
//				final SCIFIOConfig config) throws FormatException, IOException
//			{
//				super.setDest(out, imageIndex, config);
//			}

			// Writers take a source plane and save it to their attached output stream
			// The image and plane indices are references to the final output dataset
			@Override
			public void writePlane(int imageIndex, long planeIndex, Plane plane,
				long[] planeMin, long[] planeMax) throws FormatException, IOException
			{
				// This Metadata object describes how to write the data out to the
				// destination image.
				final Metadata meta = getMetadata();

				// This stream is the destination image to write to.
				final RandomAccessOutputStream stream = getStream();

				// The given Plane object is the source plane to write
				final byte[] bytes = plane.getBytes();

				System.out.println(bytes.length);
			}

			// If your writer supports a compression type, you can declare that here.
			// Otherwise it is sufficient to return an empty String[]
			@Override
			protected String[] makeCompressionTypes() {
				return new String[0];
			}
		}

		// The purpose of a Translator is similar to that of a Parser: to populate
		// the format-specific metadata of a Metadata object.
		// However, while a Parser reads from an image source to perform this
		// operation, a Translator reads from a Metadata object of another format.
		//
		// There are two main reasons when you would want to implement a Translator:
		// 1) If you implement a Writer, you should also implement a Translator to
		// describe how io.scif.Metadata should be translated to your Format-
		// specific metadata. This translator will then be called whenever
		// SCIFIO writes out your format, and it will be able to handle any
		// input format type. Essentially this is translating ImageMetadata to
		// your format-specific metadata.
		// 2) If you are adding support for a new Metadata schema to SCIFIO, you
		// will probably want to create Translators to and from your new Metadata
		// schema and core SCIFIO Metadata classes. The purpose of these
		// Translators is to more accurately or richly capture metadata
		// information, without the lossy ImageMetadata intermediate that would
		// be used by default translators.
		// This is a more advanced use case but mentioned for completeness. See
		// https://github.com/scifio/scifio-ome-xml/tree/dec59b4f37461a248cc57b1d38f4ebe2eaa3593e/src/main/java/io/scif/ome/translators
		// for examples of this case.
		public static class Translator extends
			AbstractTranslator<io.scif.Metadata, Metadata>
		{

			// The source and dest methods are used for finding matching Translators
			// They require only trivial implementations.

			@Override
			public Class<? extends io.scif.Metadata> source() {
				return io.scif.Metadata.class;
			}

			@Override
			public Class<? extends io.scif.Metadata> dest() {
				return Metadata.class;
			}

			// ** TRANSLATION METHODS **
			// There are three translation method hooks you can use. It is critical
			// to understand that the source.getAll() method may return a DIFFERENT
			// list of ImageMetadata than what is passed to these methods.
			// This is because the source's ImageMetadata may still be the direct
			// translation of its format-specific Metadata, but the provided
			// ImageMetadata may be the result of modification - cropping, zooming,
			// etc...
			// So, DO NOT CALL:
			// - Metadata#get(int)
			// - Metadata#getAll()
			// in these methods unless you have a good reason to do so. Use the
			// ImageMetadata provided.
			//
			// There are three hooks you can use in translation:
			// 1) typedTranslate gives you access to the concrete source and
			// destination metadata objects, along with the ImageMeatadata.
			// 2) translateFormatMetadata when you want to use format-specific
			// metadata from the source (only really applicable in reason #2 above
			// for creating a Translator)
			// 3) translateImageMetadata when you want to use the source's
			// ImageMetadata (which is always the case when making a translator
			// with a general io.scif.Metadata source)

			// Not used in the general case
//			@Override
//			protected void typedTranslate(final io.scif.Metadata source,
//				final List<ImageMetadata> imageMetadata, final Metadata dest)
//			{
//				super.typedTranslate(source, imageMetadata, dest);
//			}

			// Not used in the general case
//			@Override
//			protected void translateFormatMetadata(final io.scif.Metadata source,
//				final Metadata dest)
//			{
//			}

			// Here we use the state in the ImageMetadata to populate format-specific
			// metadata
			@Override
			protected void translateImageMetadata(final List<ImageMetadata> source,
				final Metadata dest)
			{
				ImageMetadata iMeta = source.get(0);
				//if (iMeta.isIndexed()) {
				//	dest.setColor("red");
				//}
				//else {
				//	dest.setColor("blue");
				//}
			}
		}
	}
	// *** END OF SAMPLE FORMAT ***

	// This method is provided simply to confirm the format is discovered
	public static void main(final String... args) throws FormatException {
		// ------------------------------------------------------------------------
		// COMPARISON WITH BIO-FORMATS 4.X
		// In Bio-Formats 4.X, adding support for a new format required modifying
		// a hard-coded list of readers or writers. This could be a significant
		// barrier for including new formats.
		// In SCIFIO, we allow formats to be discovered automatically via
		// annotation processing, or by manually adding it to a Context.
		// ------------------------------------------------------------------------

		// Let's start by creating a new context as we have in the other tutorials.
		// Creating a SCIFIO implicitly creates a new Context. During the
		// construction of this Context, services are loaded and plugins discovered
		// automatically. See the Context class documentation for more information
		// on controlling Context creation.
		final SCIFIO scifio = new SCIFIO();

		// ... and a sample image path:
		final String sampleImage = "notAnImage.dat";

		// As SampleFormat below was annotated as a @Plugin it should be available
		// to our context:
		final Format format = scifio.format().getFormat(sampleImage);

		// Verify that we found the format plugin
		System.out.println("UKSoft found via FormatService: " +
			(format != null));

		scifio.getContext().dispose();
	}

}
	
}