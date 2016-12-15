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
import io.scif.ByteArrayPlane;
import io.scif.ByteArrayReader;
import io.scif.Field;
import io.scif.Format;
import io.scif.FormatException;
import io.scif.HasColorTable;
import io.scif.ImageMetadata;
import io.scif.MetadataLevel;
import io.scif.SCIFIO;
import io.scif.config.SCIFIOConfig;
import io.scif.io.RandomAccessInputStream;
import io.scif.util.FormatTools;

import java.io.IOException;

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
			public double startvoltage=0.0;
			@Field(label="Temperature")
			private double temperature=25.0;
			@Field(label="Azimuth")
			private double azimuth=360.0;
			@Field(label="Pressure")
			private double pressure=0.0;
			@Field(label="time")
			private long time=0L;
			@Field(label="Date")
			private String date="1/1/1 00:00 +0200";
			@Field(label="micrometer_x")
			private double micrometer_x=0.0;
			@Field(label="micrometer_y")
			private double micrometer_y=0.0;

			@Field(label="offset")
			private int offset;

			public double getStartVoltage() {
				return startvoltage;
			}
			public void setStartVoltage(double startvoltage) {
				this.startvoltage=startvoltage;
			}

			public double getTemperature() {
				return temperature;
			}
			public void setTemperature(double temperature) {
				this.temperature=temperature;
			}
			
			public double getAzimuth() {
				return azimuth;
			}
			public void setAzimuth(double azimuth) {
				this.azimuth=azimuth;
			}
			
			public double getPressure() {
				return pressure;
			}
			public void setPressure(double pressure) {
				this.pressure=pressure;
			}
			
			public double getMicrometerX() {
				return micrometer_x;
			}
			public void setMicrometerX(double micrometer_x) {
				this.micrometer_x=micrometer_x;
			}
			
			public double getMicrometerY() {
				return micrometer_y;
			}
			public void setMicrometerY(double micrometer_y) {
				this.micrometer_y=micrometer_y;
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
				long filelength=stream.length();
				// Minimum information required to read the file
				stream.seek(40);
				int UKFH_width = stream.readUnsignedShort();
				int UKFH_height= stream.readUnsignedShort();
				int UKFH_nimages = stream.readUnsignedShort();
				iMeta.addAxis(Axes.X, UKFH_width);
				iMeta.addAxis(Axes.Y, UKFH_height);
				meta.setOffset((int)filelength-2*UKFH_width*UKFH_height); // This only works for a single image!
				final MetadataLevel level = config.parserGetLevel();
				config.parserIsFiltered();
				config.parserIsSaveOriginalMetadata();
				if (level != MetadataLevel.MINIMUM) {
					// File header, starts with magic string
					stream.seek(20);
					int UKFH_size = stream.readUnsignedShort();
					int UKFH_version = stream.readUnsignedShort();
					int UKFH_bitsperpixel= stream.readUnsignedShort();
					//addGlobalMeta("bitsperpixel",UKFH_bitsperpixel);
					if (UKFH_version>7) {
						int UKFH_camerabitsperpixel=stream.readUnsignedShort();
						int UKFH_MCPdiameterinpixels=stream.readUnsignedShort();
						int UKFH_hbinning=stream.readUnsignedByte();
						int UKFH_vbinning=stream.readUnsignedByte();
						log().info("More info (v>7): camerabitsperpixel "+UKFH_camerabitsperpixel+" MCPDiam "+UKFH_MCPdiameterinpixels+
								" binning "+UKFH_hbinning+"x"+UKFH_vbinning);
					}
					log().info("UView Plugin UKFileHeader info\n "+"size "+UKFH_size+"\n version "+UKFH_version+"\n bitsperpixel "+UKFH_bitsperpixel+"\n witdth "+UKFH_width+
							"\n height "+UKFH_height+"\n nimages "+UKFH_nimages);
					int UKFH_attachedrecipesize;
					if (UKFH_version>6) {
						UKFH_attachedrecipesize=stream.readUnsignedShort();
					} else {
						UKFH_attachedrecipesize=0;
					}
					stream.seek(UKFH_size+UKFH_attachedrecipesize);
					// Image header
					int UKIH_size= stream.readUnsignedShort();
					int UKIH_version= stream.readUnsignedShort();
					int UKIH_colorlow= stream.readUnsignedShort();
					int UKIH_colorhigh= stream.readUnsignedShort();
					long UKIH_time= stream.readLong();
					DateFormat formatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss Z");
					String UKIH_date=formatter.format(new Date((UKIH_time - 116444736000000000L)/10000L ));
					log().info(UKIH_date);
					int UKIH_maskx = stream.readUnsignedShort();
					int UKIH_masky = stream.readUnsignedShort();
					stream.skip(2);
					int UKIH_attachedmarkedsize=stream.readUnsignedShort();
					int MARKUP_size=128*((UKIH_attachedmarkedsize/128)+1);
					log().info("markup "+MARKUP_size);
					int UKIH_spin = stream.readUnsignedShort();
					int UKIH_leemdataversion= stream.readUnsignedShort();
					log().info("UView Plugin UKImageHeader info\n "+"size "+UKIH_size+"\n version "+UKIH_version+
							"\n ColorLow "+UKIH_colorlow+"\n ColorHigh "+UKIH_colorhigh+
							"\n time "+UKIH_time+ "\n maskx "+UKIH_maskx+"\n masky "+UKIH_masky+
							"\n spin "+UKIH_spin+ "\n leemdataversion "+UKIH_leemdataversion+" attachedmarkedsize "+UKIH_attachedmarkedsize);
					//meta.setOffset(UKFH_size+UKFH_attachedrecipesize+UKIH_size+MARKUP_size);
					log().info("Size of headers: Fileheader "+UKFH_size+" recipesize "+UKFH_attachedrecipesize+
							" Imageheader "+UKIH_size+" Markup "+MARKUP_size);
					log().info("total offset: "+(UKFH_size+UKFH_attachedrecipesize+UKIH_size+MARKUP_size));
					log().info("teoretical offset: "+((int)filelength-2*UKFH_width*UKFH_height));

					if (UKIH_version>4) {		
						stream.seek(UKFH_size+28);
						log().info("UView Plugin LEEMDATA info\n"); 
						int i=0;
						int tag;
						while (i<256) {
							tag=stream.readUnsignedByte();
							i++;
							if (tag==255){
								log().info("End of LEEMData "+tag+" "+i);
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
									meta.setMicrometerX(UKLD_micrometerx);
									meta.setMicrometerY(UKLD_micrometery);
									log().info(" micrometerxy "+UKLD_micrometerx+","+UKLD_micrometery);
									i+=8;
									break;
								case 101:
									String UKLD_fov=stream.readCString();
									log().info(" fov "+UKLD_fov);
									i+=UKLD_fov.length();
									break;
								case 102:
									float UKLD_varian0=stream.readFloat();
									log().info(" varian0 "+UKLD_varian0);
									i+=4;
									break;
								case 103:
									float UKLD_varian1=stream.readFloat();
									log().info(" varian1 "+UKLD_varian1);
									i+=4;
									break;
								case 104:
									float UKLD_camera_exposure=stream.readFloat();
									log().info(" camera_exposure "+UKLD_camera_exposure);
									if (UKIH_leemdataversion>1) {
										int b1=stream.readByte();
										int b2=stream.readByte();
										if (b1<0) {
											log().info("sliding average");
										} else if (b1==0) {
											log().info("No average");
										} else {
											log().info("Average "+b2);
										}
									}
									i+=4;
									break;
								case 105:
									String UKLD_title=stream.readCString();
									log().info(" title "+UKLD_title);
									i+=UKLD_title.length();
									break;
								case 106:
									String UKLD_gauge1=stream.readCString();
									String UKLD_gauge1units=stream.readCString();
									float UKLD_gauge1value=stream.readFloat();
									log().info(" Gauge1 "+UKLD_gauge1+" "+UKLD_gauge1value+UKLD_gauge1units);
									i+=UKLD_gauge1.length()+UKLD_gauge1.length()+4;
									break;
								case 107:
									String UKLD_gauge2=stream.readCString();
									String UKLD_gauge2units=stream.readCString();
									float UKLD_gauge2value=stream.readFloat();
									log().info(" Gauge2 "+UKLD_gauge2+" "+UKLD_gauge2value+UKLD_gauge2units);
									i+=UKLD_gauge2.length()+UKLD_gauge2.length()+4;
									break;	
								case 108:
									String UKLD_gauge3=stream.readCString();
									String UKLD_gauge3units=stream.readCString();
									float UKLD_gauge3value=stream.readFloat();
									log().info(" Gauge3 "+UKLD_gauge3+" "+UKLD_gauge3value+UKLD_gauge3units);
									i+=UKLD_gauge3.length()+UKLD_gauge3.length()+4;
									break;
								case 109:
									String UKLD_gauge4=stream.readCString();
									String UKLD_gauge4units=stream.readCString();
									float UKLD_gauge4value=stream.readFloat();
									log().info(" Gauge4 "+UKLD_gauge4+" "+UKLD_gauge4value+UKLD_gauge4units);
									i+=UKLD_gauge4.length()+UKLD_gauge4.length()+4;
									break;
								case 110:
									String UKLD_fovcal=stream.readCString();
									float UKLD_fovcalvalue=stream.readFloat();
									log().info(" FOV calibration "+UKLD_fovcal+" "+UKLD_fovcalvalue);
									i+=UKLD_fovcal.length()+4;
									break;
								case 111:
									float UKLD_phi=stream.readFloat();
									float UKLD_theta=stream.readFloat();
									log().info(" Phi, Theta "+UKLD_phi+" "+UKLD_theta);
									i+=8;
									break;
								case 115:
									float UKLD_MCPscreenvoltage=stream.readFloat();
									log().info(" MCP screen voltage "+UKLD_MCPscreenvoltage);
									i+=4;
									break;
								case 116:
									float UKLD_MCPchannelplate=stream.readFloat();
									log().info(" MCP channelplate "+UKLD_MCPchannelplate);
									i+=4;
									break;
								default:
									if (tag<100) {
										String module=stream.readCString();
										float module_reading=stream.readFloat();
										log().info(" Module "+tag+" "+module+" "+module_reading);
										i+=module.length()+4;
										break;
									} else if (tag>128) {
										tag=tag-128;
										String module=stream.readCString();
										float module_reading=stream.readFloat();
										log().info(" Module* "+tag+" "+module+" "+module_reading);
										i+=module.length()+4;
										break;
									}

								}
							}
						}	}

				}


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