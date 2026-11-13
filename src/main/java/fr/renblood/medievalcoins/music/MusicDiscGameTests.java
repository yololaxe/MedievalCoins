package fr.renblood.medievalcoins.music;

import fr.renblood.medievalcoins.init.ItemInit;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;

@GameTestHolder("medieval_coins")
@PrefixGameTestTemplate(false)
public final class MusicDiscGameTests {
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void sixteenDiscsHaveValidNativeAudioAndRecipes(GameTestHelper h)throws Exception {
        h.assertTrue(MusicDiscs.DISCS.size()==16&&MusicDiscs.DISCS.get(2)==ItemInit.PIPPIN_MUSIC_DISC,"Sixteen discs, preserving original identity");
        long count=ItemInit.REGISTRY.getEntries().stream().filter(item->item.get() instanceof RecordItem).count();h.assertTrue(count==16,"Exactly sixteen music discs in this mod");
        for(int n=1;n<=16;n++) {
            var record=(RecordItem)MusicDiscs.DISCS.get(n).get();h.assertTrue(record.getDefaultInstance().is(net.minecraft.tags.ItemTags.MUSIC_DISCS),"Every disc is tagged for the jukebox");
            h.assertTrue(record.getSound()!=null&&record.getLengthInTicks()>2000&&record.getAnalogOutput()>=1&&record.getAnalogOutput()<=15,"Native sound, duration and comparator output are valid");
            var id=new net.minecraft.resources.ResourceLocation("medieval_coins",MusicDiscs.IDS[n-1]+"_engraving");h.assertTrue(h.getLevel().getRecipeManager().byKey(id).isPresent(),"Every disc has an engraving craft");
            try(var stream=MusicDiscGameTests.class.getResourceAsStream("/assets/medieval_coins/sounds/music/renblood_"+String.format(java.util.Locale.ROOT,"%02d",n)+".ogg")) {
                h.assertTrue(stream!=null,"Audio file is packaged");byte[] header=stream.readNBytes(64);int offset=27+Byte.toUnsignedInt(header[26]);h.assertTrue(header[0]=='O'&&header[1]=='g'&&header[offset]==1&&header[offset+1]=='v',"First logical stream is Vorbis, never a cover-video stream");
                h.assertTrue(Byte.toUnsignedInt(header[offset+11])==1,"Every track is mono for positional jukebox audio");
            }
        }
        h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void eachDiscStartsAndStopsInRealJukebox(GameTestHelper h) {
        BlockPos pos=new BlockPos(1,1,1);h.setBlock(pos,Blocks.JUKEBOX);var box=(JukeboxBlockEntity)h.getBlockEntity(pos);
        for(var item:MusicDiscs.DISCS.values()) {
            box.setItem(0,new ItemStack(item.get()));h.assertTrue(box.isRecordPlaying()&&box.getItem(0).is(item.get()),"Jukebox accepts and starts every new record");
            box.removeItem(0,1);h.assertTrue(!box.isRecordPlaying()&&box.getItem(0).isEmpty(),"Removing the disc stops playback and empties the slot");
        }
        h.succeed();
    }
}
