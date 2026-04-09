/**
 * Scifio-UView plugin. This plugin reads single images from the UKSOFT2000 format. This format is used by the
 * Elmitec camera acquisition program for their LEEM/PEEM line of instruments.
 *
 * It is a simple unsigned 16bit binary dump preceded by a header with some experimental parameters and the size.
 * For the record, it is the same format originally used in a Transputer electronics control unit for Scanning Tunneling
 * Microscopy, from Uwe Knipping (who then moved to Elmitec).
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
import io.scif.util.FormatTools;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.imagej.axis.Axes;
import net.imglib2.Interval;

import org.scijava.io.handle.DataHandle;
import org.scijava.io.location.FileLocation;
import org.scijava.io.location.Location;
import org.scijava.plugin.Plugin;



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
			protected void typedParse(final DataHandle<Location> stream,
					final Metadata meta, final SCIFIOConfig config) throws IOException,
			FormatException
			{
				meta.createImageMetadata(1);
				final ImageMetadata iMeta = meta.get(0);
				stream.setOrder(DataHandle.ByteOrder.LITTLE_ENDIAN);
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
				if (level != MetadataLevel.MINIMUM) {
					// File header, starts with magic string
					stream.seek(20);
					int UKFH_size = stream.readUnsignedShort();
					int UKFH_version = stream.readUnsignedShort();
					int UKFH_bitsperpixel= stream.readUnsignedShort();
					if (UKFH_version>7) {
						int UKFH_camerabitsperpixel=stream.readUnsignedShort();
						int UKFH_MCPdiameterinpixels=stream.readUnsignedShort();
						int UKFH_hbinning=stream.readUnsignedByte();
						int UKFH_vbinning=stream.readUnsignedByte();
					}
					// attachedRecipeSize is always at absolute offset 46 in the file header
					// (per spec: file header is 104 bytes fixed, attachedRecipeSize at offset 46)
					int UKFH_attachedrecipesize;
					if (UKFH_version>6) {
						stream.seek(46);
						UKFH_attachedrecipesize=stream.readUnsignedShort();
					} else {
						UKFH_attachedrecipesize=0;
					}
					// The recipe block on disk is always 128 bytes when present (attachedRecipeSize > 0)
					int recipeBlockSize = (UKFH_attachedrecipesize > 0) ? 128 : 0;
					stream.seek(UKFH_size + recipeBlockSize);
					// Image header
					int UKIH_size= stream.readUnsignedShort();
					int UKIH_version= stream.readUnsignedShort();
					int UKIH_colorlow= stream.readUnsignedShort();
					int UKIH_colorhigh= stream.readUnsignedShort();
					long UKIH_time= stream.readLong();
					DateFormat formatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss Z");
					String UKIH_date=formatter.format(new Date((UKIH_time - 116444736000000000L)/10000L ));
					meta.getTable().put("Date", UKIH_date);
					int UKIH_maskx = stream.readUnsignedShort();
					int UKIH_masky = stream.readUnsignedShort();
					stream.seek(stream.offset()+2); // skip RotateMask (2 bytes)
					int UKIH_attachedmarkedsize=stream.readUnsignedShort();
					int MARKUP_size=128*((UKIH_attachedmarkedsize/128)+1);
					int UKIH_spin = stream.readUnsignedShort();
					int UKIH_leemdataversion= stream.readUnsignedShort();

					if (UKIH_version>4) {
						// LEEMdata[239] is at offset 28 within the image header
						stream.seek(UKFH_size + recipeBlockSize + 28);
						int i=0;
						int tag;
						while (i<256) {
							tag=stream.readUnsignedByte();
							i++;
							if (tag==255){
								break;
							} else {
								switch (tag) {
								case 16:
									stream.readUnsignedByte();
									i+=1;
									break;
								case 100:
									float UKLD_micrometerx=stream.readFloat();
									float UKLD_micrometery=stream.readFloat();
									meta.setMicrometerX(UKLD_micrometerx);
									meta.setMicrometerY(UKLD_micrometery);
									meta.getTable().put("MicrometerX", UKLD_micrometerx);
									meta.getTable().put("MicrometerY", UKLD_micrometery);
									i+=8;
									break;
								case 101:
									String UKLD_fov=stream.readCString();
									meta.getTable().put("FOV", UKLD_fov);
									i+=UKLD_fov.length()+1;
									break;
								case 102:
									float UKLD_varian0=stream.readFloat();
									meta.getTable().put("Varian1", UKLD_varian0);
									i+=4;
									break;
								case 103:
									float UKLD_varian1=stream.readFloat();
									meta.getTable().put("Varian2", UKLD_varian1);
									i+=4;
									break;
								case 104:
									float UKLD_camera_exposure=stream.readFloat();
									meta.getTable().put("CameraExposure", UKLD_camera_exposure);
									if (UKIH_leemdataversion>1) {
										stream.readByte();
										stream.readByte();
										i+=2;
									}
									i+=4;
									break;
								case 105:
									String UKLD_title=stream.readCString();
									meta.getTable().put("Title", UKLD_title);
									i+=UKLD_title.length()+1;
									break;
								case 106:
									String UKLD_gauge1=stream.readCString();
									String UKLD_gauge1units=stream.readCString();
									float UKLD_gauge1value=stream.readFloat();
									meta.getTable().put(UKLD_gauge1 + " (" + UKLD_gauge1units + ")", UKLD_gauge1value);
									i+=UKLD_gauge1.length()+1+UKLD_gauge1units.length()+1+4;
									break;
								case 107:
									String UKLD_gauge2=stream.readCString();
									String UKLD_gauge2units=stream.readCString();
									float UKLD_gauge2value=stream.readFloat();
									meta.getTable().put(UKLD_gauge2 + " (" + UKLD_gauge2units + ")", UKLD_gauge2value);
									i+=UKLD_gauge2.length()+1+UKLD_gauge2units.length()+1+4;
									break;
								case 108:
									String UKLD_gauge3=stream.readCString();
									String UKLD_gauge3units=stream.readCString();
									float UKLD_gauge3value=stream.readFloat();
									meta.getTable().put(UKLD_gauge3 + " (" + UKLD_gauge3units + ")", UKLD_gauge3value);
									i+=UKLD_gauge3.length()+1+UKLD_gauge3units.length()+1+4;
									break;
								case 108+1:
									String UKLD_gauge4=stream.readCString();
									String UKLD_gauge4units=stream.readCString();
									float UKLD_gauge4value=stream.readFloat();
									meta.getTable().put(UKLD_gauge4 + " (" + UKLD_gauge4units + ")", UKLD_gauge4value);
									i+=UKLD_gauge4.length()+1+UKLD_gauge4units.length()+1+4;
									break;
								case 110:
									String UKLD_fovcal=stream.readCString();
									float UKLD_fovcalvalue=stream.readFloat();
									meta.getTable().put("FOVCalibration", UKLD_fovcalvalue);
									meta.getTable().put("FOVCalibrationUnit", UKLD_fovcal);
									i+=UKLD_fovcal.length()+1+4;
									break;
								case 111:
									float UKLD_phi=stream.readFloat();
									float UKLD_theta=stream.readFloat();
									meta.getTable().put("Phi", UKLD_phi);
									meta.getTable().put("Theta", UKLD_theta);
									i+=8;
									break;
								case 115:
									float UKLD_MCPscreenvoltage=stream.readFloat();
									meta.getTable().put("MCPScreenVoltage", UKLD_MCPscreenvoltage);
									i+=4;
									break;
								case 116:
									float UKLD_MCPchannelplate=stream.readFloat();
									meta.getTable().put("MCPChannelPlate", UKLD_MCPchannelplate);
									i+=4;
									break;
								default:
									if (tag<100) {
										String module=stream.readCString();
										float module_reading=stream.readFloat();
										meta.getTable().put(module, module_reading);
										i+=module.length()+1+4;
										break;
									} else if (tag>128) {
										tag=tag-128;
										String module=stream.readCString();
										float module_reading=stream.readFloat();
										meta.getTable().put(module, module_reading);
										i+=module.length()+1+4;
										break;
									}

								}
							}
						}
					}
				}
			}
		}

		public static class Checker extends AbstractChecker {

			@Override
			public boolean suffixSufficient() {
				return true;
			}

			@Override
			public boolean suffixNecessary() {
				return true;
			}

			@Override
			public boolean isFormat(final DataHandle<Location> in)
					throws IOException
			{
				final int blockLen = UVIEW_MAGIC_STRING.length();
				if (!FormatTools.validStream(in, blockLen, false)) return false;
				return in.readString(blockLen).startsWith(UVIEW_MAGIC_STRING);
			}
		}

		public static class Reader extends ByteArrayReader<Metadata> {
			@Override
			public ByteArrayPlane openPlane(int imageIndex, long planeIndex,
					ByteArrayPlane plane, Interval bounds,
					SCIFIOConfig config) throws FormatException, IOException
			{
				final Metadata meta = getMetadata();
				final byte[] buf = plane.getBytes();

				FormatTools.checkPlaneForReading(meta, imageIndex, planeIndex,
						buf.length, bounds);

				int width=(int)meta.get(imageIndex).getAxisLength(Axes.X);
				int height=(int)meta.get(imageIndex).getAxisLength(Axes.Y);
				getHandle().seek(meta.getOffset());
				for(int i=0;i<height;i++) {
					getHandle().readFully(buf,(height-1-i)*width*2,width*2); // Need to flip vertically
				}
				if (meta instanceof HasColorTable) {
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

		// This method is provided simply to confirm the format is discovered
		public static void main(final String... args) throws FormatException {

			final SCIFIO scifio = new SCIFIO();
			final Location sampleImage = new FileLocation("notAnImage.dat");
			final Format format = scifio.format().getFormat(sampleImage);
			System.out.println("UKSoft found via FormatService: " +
					(format != null));

			scifio.getContext().dispose();
		}

	}

}
