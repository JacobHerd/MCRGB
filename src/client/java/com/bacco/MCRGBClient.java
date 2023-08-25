package com.bacco;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.spi.RegisterableService;
import javax.swing.GroupLayout.Group;

import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.Struct;
import org.lwjgl.system.windows.KEYBDINPUT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bacco.event.KeyInputHandler;
import com.bacco.gui.ColourInventoryScreen;
import com.google.gson.Gson;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.fabricmc.fabric.api.event.client.player.ClientPickBlockApplyCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.State;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

public class MCRGBClient implements ClientModInitializer {
	public static final BlockColourStorage[] loadedBlockColourArray = new Gson().fromJson(readJson(), BlockColourStorage[].class);
	public static final Logger LOGGER = LoggerFactory.getLogger("mcrgb");
	public static final boolean readMode = false;
	public net.minecraft.client.MinecraftClient client;
	int totalBlocks = 0;			
	int fails = 0;
	int successes = 0;
	public static ColourInventoryScreen colourInvScreen;

	@Override
	public void onInitializeClient() {
		
		KeyInputHandler.register(this);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, _client) -> {
			client = _client;
			colourInvScreen = new ColourInventoryScreen(client);

			//Read from JSON
			try{
			BlockColourStorage[] loadedBlockColourArray = new Gson().fromJson(readJson(), BlockColourStorage[].class);
			Registries.BLOCK.forEach(block -> {
				for(BlockColourStorage storage : loadedBlockColourArray){
					if(storage.block.equals(block.asItem().getTranslationKey())){
						storage.spriteDetails.forEach(details -> {	
							((IItemBlockColourSaver) block.asItem()).addSpriteDetails(details);
						});
						break;
					};
				}

			});
			}catch(Exception e){
				RefreshColours();
				client.player.sendMessage(Text.literal("MCRGB: Reloaded Colours"));
			}
		});

		//Override item tooltips to display the colour.
		ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
			IItemBlockColourSaver item = (IItemBlockColourSaver) stack.getItem();
			for(int i = 0; i < item.getLength(); i++){
				ArrayList<String> strings = item.getSpriteDetails(i).getStrings();
				strings.forEach(string -> {
				var text = Text.literal(string);
				var message = text.formatted(Formatting.AQUA);
				lines.add(message);
				});		
			}
		});

	}

	public static void writeJson(String str)
        throws IOException
    {
        try {
			String path = "./mcrgb_colours/";
            String fileName = "file.json";
			File dir = new File(path);
			File file = new File(dir, fileName);
			if(!dir.exists()){
				dir.mkdir();
			}
			if(!file.exists()){
				file.createNewFile();
			}
            FileWriter fw = new FileWriter(file);
  
            // read each character from string and write
            // into FileWriter
            for (int i = 0; i < str.length(); i++)
                fw.write(str.charAt(i));
  
  
            // close the file
            fw.close();
        }
        catch (Exception e) {
            e.getStackTrace();
        }
    }

	public static String readJson()
    {
        try {
            // FileReader Class used
            FileReader fileReader
                = new FileReader("./mcrgb_colours/file.json");
 
            int i;
			String str = "";
            // Using read method
            while ((i = fileReader.read()) != -1) {
				str += (char)i;
            }
 
            // Close method called
            fileReader.close();
			return str;
        }
        catch (Exception e) {
			return "";
        }
    }

	public static String rgbToHex(int r, int g, int b){
		String hexR = r < 0x10 ? "0" + Integer.toHexString(r) : Integer.toHexString(r);
		String hexG = g < 0x10 ? "0" + Integer.toHexString(g) : Integer.toHexString(g);
		String hexB = b < 0x10 ? "0" + Integer.toHexString(b) : Integer.toHexString(b);
		return ("#" + hexR + hexG + hexB).toUpperCase();
	}

	public static Vector3i hexToRGB(String hex){
		hex = hex.replace("#","");
		int hexint = Integer.parseInt(hex,16);
		if(hexint > 0xFFFFFF || hexint < 0x000000) return new Vector3i(0,0,0);
		int b = hexint & 0x0000FF;
		int g = (hexint & 0x00FF00)/0x100;
		int r = (hexint & 0xFF0000)/(0x10000);
		return new Vector3i(r, g, b);
	}

	//Calculate the dominant colours in a list of colours
	public static Set<ColourGroup> GroupColours(ArrayList<Vector3i> rgblist){
		Set<ColourGroup> groups = new HashSet<ColourGroup>();
		
		//Loop through every pixel
		for (int i = 0; i < rgblist.size(); i++){
			Vector3i iPix = new Vector3i(rgblist.get(i).x,rgblist.get(i).y,rgblist.get(i).z);
			//x is r; y is g; z is b

			//check if already in a group
			boolean iInGroup = false;
			for (ColourGroup group : groups){
				if (group.pixels.contains(iPix)){
					iInGroup = true;
					break;
				}
			}

			//if i is not in a group, create a new one and add i to it...
			if(!iInGroup){
				ColourGroup newGroup = new ColourGroup();
				newGroup.pixels.add(iPix);

				//loop through all the pixels after i, and compare them to i
				for (int j = i + 1; j < rgblist.size(); j++){
					//if the distance is less than 100, add j to the group (if it is not already in a group)
					Vector3i jPix = new Vector3i(rgblist.get(j).x,rgblist.get(j).y,rgblist.get(j).z);
					if(jPix.distance(iPix) < 100){
						boolean jInGroup = false;
						for (ColourGroup group : groups){
							if (group.pixels.contains(jPix)){
								jInGroup = true;
								break;
							}
						}

						if(!jInGroup){
							newGroup.pixels.add(jPix);
						}
					}

				}
				//finally, add the new group to the list of groups
				groups.add(newGroup);
			}
		}
		//calculate the average rgb value of each group, convert to hex and calculate weight
		for (ColourGroup group : groups){
			Vector3i sum = new Vector3i(0, 0, 0);
			int counter = 0;
			for (Vector3i colour : group.pixels){
				sum.add(colour);
				counter ++;
			}
			if(counter == 0){return null;}
			Vector3i avg = sum.div(counter);
			group.meanColour = new Vector3i(avg.x, avg.y, avg.z);
			group.meanHex = rgbToHex(avg.x, avg.y, avg.z);
			group.weight = (int)((float)counter/(float)rgblist.size() * 100);
		}

		return groups;
	}

	public void RefreshColours(){
		if (client == null) return;
		//get top sprite of stone block default state
		var defSprite = client.getBakedModelManager().getBlockModels().getModel(Blocks.STONE.getDefaultState()).getQuads(Blocks.STONE.getDefaultState(), Direction.UP, Random.create()).get(0).getSprite();
		//get id of the atlas containing above
		var atlas = defSprite.getAtlasId();
		//use atlas id to get OpenGL ID. Atlas contains ALL blocks
		int glID = client.getTextureManager().getTexture(atlas).getGlId();
		//get width and height from OpenGL by binding texture
		RenderSystem.bindTexture(glID);
		int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
		int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
		int size = width * height;
		//Make byte buffer and load full atlas into buffer.
		ByteBuffer buffer = BufferUtils.createByteBuffer(size*4);
		GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
		//convert buffer to an array of bytes
		byte[] pixels = new byte[size*4];
		buffer.get(pixels);
		ArrayList<BlockColourStorage> blockColourList = new ArrayList<BlockColourStorage>();
		//loop through every block in the game
		Registries.BLOCK.forEach(block -> {
			if(block.asItem().getTranslationKey() == Items.AIR.getTranslationKey()) return;
			((IItemBlockColourSaver) block.asItem()).clearSpriteDetails();
			BlockColourStorage storage = new BlockColourStorage();
			totalBlocks +=1;
			Set<Sprite> sprites = new HashSet<Sprite>();
			//try to get the default top texture sprite. if fails, report error and skip this block
			
			block.getStateManager().getStates().forEach(state -> {
				try{
					var model = client.getBakedModelManager().getBlockModels().getModel(state);						
					sprites.add(model.getQuads(state, Direction.UP, Random.create()).get(0).getSprite());
					sprites.add(model.getQuads(state, Direction.DOWN, Random.create()).get(0).getSprite());
					sprites.add(model.getQuads(state, Direction.NORTH, Random.create()).get(0).getSprite());
					sprites.add(model.getQuads(state, Direction.SOUTH, Random.create()).get(0).getSprite());
					sprites.add(model.getQuads(state, Direction.EAST, Random.create()).get(0).getSprite());
					sprites.add(model.getQuads(state, Direction.WEST, Random.create()).get(0).getSprite());
					successes +=1;
				}catch(Exception e){	
					fails +=1;						
					return;
				}
			});
			if(sprites.size() < 1){
				return;
			}
			sprites.forEach(sprite -> {
				//get coords of sprite in atlas
				int spriteX = sprite.getX();
				int spriteY = sprite.getY();
				int spriteW = sprite.getContents().getWidth();
				int spriteH = sprite.getContents().getHeight();
				//convert coords to byte position
				int firstPixel = (spriteY*width + spriteX)*4;
				ArrayList<Vector3i> rgbList = new ArrayList<Vector3i>();
				//for each horizontal row in the sprite
				for (int row = 0; row < spriteH; row++){
					int firstInRow = firstPixel + row*width*4;
					//loop from first pixel in row to the sprite width.
					//Note: Looping in increments of 4, because each pixel is 4 bytes. (R,G,B and A)
					for (int pos = firstInRow; pos < firstInRow + 4*spriteW; pos+=4){
						//retrieve bytes for RGBA values
						//"& 0xFF" does logical and with 11111111. this extracts the last 8 bits, converting to unsigned int
						int a = pixels[pos+3];
						a = a & 0xFF;
						//if the pixel is fully transparent, skip it and don't count it
						if(a > 0) {
							Vector3i c = new Vector3i(pixels[pos] & 0xFF, pixels[pos+1] & 0xFF, pixels[pos+2] & 0xFF);
							rgbList.add(c);
						}
					}
				}
				//Calculate the dominant colours
				Set<ColourGroup> colourGroups = GroupColours(rgbList);
				if (colourGroups == null) return;

				//Add sprite name and each dominant colour to the IItemBlockColourSaver
				SpriteDetails spriteDetails = new SpriteDetails();
				String[] namesplit = sprite.getContents().getId().toString().split("/");
				String name = namesplit[namesplit.length-1];
				spriteDetails.name = name;
				colourGroups.forEach(cg -> {	
					spriteDetails.colourinfo.add(cg.meanColour);
					spriteDetails.weights.add(cg.weight);
				});
				storage.block = block.asItem().getTranslationKey();
				storage.spriteDetails.add(spriteDetails);
			});				
			storage.spriteDetails.forEach(details -> {
				((IItemBlockColourSaver) block.asItem()).addSpriteDetails(details);
			});
			blockColourList.add(storage);
		});

		//Write arraylist to json
		Gson gson = new Gson();
		String blockColoursJson = gson.toJson(blockColourList);
		try {
			writeJson(blockColoursJson);
		} catch (IOException e) {
		}
		client.player.sendMessage(Text.literal("MCRGB: Reloaded Colours"));
	}
}