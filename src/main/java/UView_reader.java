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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;



public class UView_reader {
	
	@Plugin(type = Format.class)
	
	public static class UKFormat extends AbstractFormat {

		public static final String UVIEW_MAGIC_STRING = "UKSOFT2001";
		
		@Override
		public String getFormatName() {
			return "UKSOFT2000/UView";
		}

		@Override
		protected String[] makeSuffixArray() {
			return new String[] { "dat" };
		}

		public static class Metadata extends AbstractMetadata {
			
			@Field(label="StartVoltage")
			private float startvoltage=0.0f;
			@Field(label="Temperature")
			private float temperature=25.0f;
			@Field(label="Azimuth")
			private float azimuth=360.0f;
			@Field(label="Pressure")
			private float pressure=0.0f;
			@Field(label="time")
			private long time=0L;
			@Field(label="Date")
			private String date="1/1/1 00:00 +0200";
			
			@Field(label="offset")
			private int offset;
			
			public float getStartVoltage() {
				return startvoltage;
			}
			
			public void setStartVoltage(float startvoltage) {
				this.startvoltage=startvoltage;
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
				
				stream.seek(20);
				int UKFH_size = stream.readUnsignedShort();
				int UKFH_version = stream.readUnsignedShort();
				int UKFH_bitsperpixel= stream.readUnsignedShort();
				stream.seek(40);
                int UKFH_width = stream.readUnsignedShort();
                int UKFH_height= stream.readUnsignedShort();
                int UKFH_nimages = stream.readUnsignedShort();
    			iMeta.addAxis(Axes.X, UKFH_width);
    			iMeta.addAxis(Axes.Y, UKFH_height);
    			log().debug("UView Plugin UKFileHeader info\n "+"size "+UKFH_size+"\n version "+UKFH_version+"\n bitsperpixel "+UKFH_bitsperpixel+"\n witdth "+UKFH_width+
    					"\n height "+UKFH_height+"\n nimages "+UKFH_nimages);
    			stream.seek(UKFH_size);
    			int UKIH_size= stream.readUnsignedShort();
    			int UKIH_version= stream.readUnsignedShort();
    			int UKIH_colorlow= stream.readUnsignedShort();
    			int UKIH_colorhigh= stream.readUnsignedShort();
    			long UKIH_time= stream.readLong();
    			DateFormat formatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss Z");
    			String UKIH_date=formatter.format(new Date((UKIH_time - 116444736000000000L)/10000L ));
    			log().debug(UKIH_date);
    			int UKIH_maskx = stream.readUnsignedShort();
    			int UKIH_masky = stream.readUnsignedShort();
    			stream.skip(4);
    			int UKIH_spin = stream.readUnsignedShort();
    			int UKIH_leemdataversion= stream.readUnsignedShort();
    			log().debug("UView Plugin UKImageHeader info\n "+"size "+UKIH_size+"\n version "+UKIH_version+"\n ColorLow "+UKIH_colorlow+"\n ColorHigh "+UKIH_colorhigh+
    					"\n time "+UKIH_time+ "\n maskx "+UKIH_maskx+"\n masky "+UKIH_masky+"\n spin "+UKIH_spin+ "\n leemdataversion "+UKIH_leemdataversion);
    			meta.setOffset(UKFH_size+UKIH_size+128); // Why the 128 extra offset?????
    			if (UKIH_version>4) {		
    				stream.seek(UKFH_size+28);
    				log().debug("UView Plugin LEEMDATA info\n"); 
    				int i=0;
    				int tag;
    				while (i<256) {
    					tag=stream.readUnsignedByte();
    					i++;
    					if (tag==255){
    						log().debug("End of LEEMData "+tag+" "+i);
    						break;
    					} else {
    					switch (tag) {
    					case 16:
    						stream.readUnsignedByte();
    						i+=2;
    						break;
    					case 100:
    						float UKLD_micrometerx=stream.readFloat();
    						float UKLD_micrometery=stream.readFloat();
    						log().debug(" micrometerxy "+UKLD_micrometerx+","+UKLD_micrometery);
    						i+=8;
    						break;
    					case 101:
    						String UKLD_fov=stream.readCString();
    						log().debug(" fov "+UKLD_fov);
    						i+=UKLD_fov.length();
    						break;
    					case 102:
    						float UKLD_varian0=stream.readFloat();
    						log().debug(" varian0 "+UKLD_varian0);
    						i+=4;
    						break;
    					case 103:
    						float UKLD_varian1=stream.readFloat();
    						log().debug(" varian1 "+UKLD_varian1);
    						i+=4;
    						break;
    					case 104:
    						float UKLD_camera_exposure=stream.readFloat();
    						log().debug(" camera_exposure "+UKLD_camera_exposure);
    						i+=4;
    						break;
    					case 105:
    						String UKLD_title=stream.readCString();
    						log().debug(" title "+UKLD_title);
    						i+=UKLD_title.length();
    						break;
    					case 106:
    						String UKLD_gauge1=stream.readCString();
    						String UKLD_gauge1units=stream.readCString();
    						float UKLD_gauge1value=stream.readFloat();
    						log().debug(" Gauge1 "+UKLD_gauge1+" "+UKLD_gauge1value+UKLD_gauge1units);
    						i+=UKLD_gauge1.length()+UKLD_gauge1.length()+4;
    						break;
    					case 107:
    						String UKLD_gauge2=stream.readCString();
    						String UKLD_gauge2units=stream.readCString();
    						float UKLD_gauge2value=stream.readFloat();
    						log().debug(" Gauge2 "+UKLD_gauge2+" "+UKLD_gauge2value+UKLD_gauge2units);
    						i+=UKLD_gauge2.length()+UKLD_gauge2.length()+4;
    						break;	
    					case 108:
    						String UKLD_gauge3=stream.readCString();
    						String UKLD_gauge3units=stream.readCString();
    						float UKLD_gauge3value=stream.readFloat();
    						log().debug(" Gauge3 "+UKLD_gauge3+" "+UKLD_gauge3value+UKLD_gauge3units);
    						i+=UKLD_gauge3.length()+UKLD_gauge3.length()+4;
    						break;
    					case 109:
    						String UKLD_gauge4=stream.readCString();
    						String UKLD_gauge4units=stream.readCString();
    						float UKLD_gauge4value=stream.readFloat();
    						log().debug(" Gauge4 "+UKLD_gauge4+" "+UKLD_gauge4value+UKLD_gauge4units);
    						i+=UKLD_gauge4.length()+UKLD_gauge4.length()+4;
    						break;
    					case 110:
    						String UKLD_fovcal=stream.readCString();
    						float UKLD_fovcalvalue=stream.readFloat();
    						log().debug(" FOV calibration "+UKLD_fovcal+" "+UKLD_fovcalvalue);
    						i+=UKLD_fovcal.length()+4;
    						break;
    					case 111:
    						float UKLD_phi=stream.readFloat();
    						float UKLD_theta=stream.readFloat();
    						log().debug(" Phi, Theta "+UKLD_phi+" "+UKLD_theta);
    						i+=8;
    						break;
    					default:
    						if (tag<100) {
    							String module=stream.readCString();
    							float module_reading=stream.readFloat();
    							log().debug(" Module "+tag+" "+module+" "+module_reading);
    							i+=module.length()+4;
    							break;
    						}}
    					}
    				}
    					
    			}
    			
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
				
				int width=(int)meta.get(0).getAxisLength(Axes.X);
				int height=(int)meta.get(0).getAxisLength(Axes.Y);
				getStream().seek(meta.getOffset());
				for(int i=0;i<height;i++) {
					getStream().read(buf,(height-1-i)*width*2,width*2); // Need to flip vertically the image
				}
				//getStream().read(buf);
				if (meta.getClass().isAssignableFrom(HasColorTable.class)) {
					plane.setColorTable(((HasColorTable) meta).getColorTable(imageIndex,
						planeIndex));
				}

				return plane;
			}

			@Override
			protected String[] createDomainArray() {
				String[] domains={FormatTools.EM_DOMAIN};
				return (domains);
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