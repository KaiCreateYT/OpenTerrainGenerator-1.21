package com.pg85.otg.neoforge.util;

import com.pg85.otg.OTG;
import com.pg85.otg.neoforge.gen.NeoForgeWorldGenRegion;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.gen.LocalWorldGenRegion;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import com.pg85.otg.util.nbt.LocalNBTHelper;
import com.pg85.otg.util.nbt.NamedBinaryTag;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.text.MessageFormat;
import java.util.Set;

public class NeoForgeNBTHelper extends LocalNBTHelper {

    private static NamedBinaryTag getNBTFromNMSTagList(String name, ListTag nmsListTag)
    {
        if (nmsListTag.isEmpty())
        {
            return null;
        }

        NamedBinaryTag.Type listType = NamedBinaryTag.Type.values()[nmsListTag.getElementType()];
        NamedBinaryTag listTag = new NamedBinaryTag(name, listType);

        for (Tag nmsChildTag : nmsListTag) {
            switch (listType) {
                case TAG_End:
                    break;
                case TAG_Byte:
                case TAG_Short:
                case TAG_Int:
                case TAG_Long:
                case TAG_Float:
                case TAG_Double:
                case TAG_Byte_Array:
                case TAG_String:
                case TAG_Int_Array:
                    listTag.addTag(new NamedBinaryTag(listType, null, getValueFromNms(nmsChildTag)));
                    break;
                case TAG_List:
                    NamedBinaryTag listChildTag = getNBTFromNMSTagList(null, (ListTag) nmsChildTag);
                    if (listChildTag != null) {
                        listTag.addTag(listChildTag);
                    }
                    break;
                case TAG_Compound:
                    listTag.addTag(getNBTFromNMSTagCompound(null, (CompoundTag) nmsChildTag));
                    break;
                default:
                    if (OTGLog.getLogger().getLogCategoryEnabled(LogCategory.CUSTOM_OBJECTS)) {
                        OTGLog.log(
                                LogLevel.ERROR,
                                LogCategory.CUSTOM_OBJECTS,
                                MessageFormat.format(
                                        "Cannot convert list subtype {0} from its NMS value",
                                        listType
                                )
                        );
                    }
                    break;
            }
        }
        return listTag;
    }

    public static NamedBinaryTag getNBTFromNMSTagCompound(String name, CompoundTag nmsCompoundTag) {
        NamedBinaryTag compoundTag = new NamedBinaryTag(NamedBinaryTag.Type.TAG_Compound, name,
                new NamedBinaryTag[]{new NamedBinaryTag(NamedBinaryTag.Type.TAG_End, null, null)});

        Set<String> keys = nmsCompoundTag.getAllKeys();

        for (String key : keys)
        {
            Tag nmsChildTag = nmsCompoundTag.get(key);

            if (nmsChildTag == null)
            {
                if(OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.CUSTOM_OBJECTS))
                {
                    OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.CUSTOM_OBJECTS,
                            "Failed to read NBT property " + key + " from tag " + nmsCompoundTag.getId());
                }
                continue;
            }

            NamedBinaryTag.Type type = NamedBinaryTag.Type.values()[nmsChildTag.getId()];
            switch (type)
            {
                case TAG_End:
                    break;
                case TAG_Byte:
                case TAG_Short:
                case TAG_Int:
                case TAG_Long:
                case TAG_Float:
                case TAG_Double:
                case TAG_Byte_Array:
                case TAG_String:
                case TAG_Int_Array:
                    compoundTag.addTag(new NamedBinaryTag(type, key, getValueFromNms(nmsChildTag)));
                    break;
                case TAG_List:
                    NamedBinaryTag listChildTag = getNBTFromNMSTagList(key, (ListTag) nmsChildTag);
                    if (listChildTag != null)
                    {
                        compoundTag.addTag(listChildTag);
                    }
                    break;
                case TAG_Compound:
                    compoundTag.addTag(getNBTFromNMSTagCompound(key, (CompoundTag) nmsChildTag));
                    break;
                default:
                    break;
            }
        }

        return compoundTag;
    }

    @Override
    public NamedBinaryTag getNBTFromLocation(LocalWorldGenRegion world, int x, int y, int z) {
        BlockEntity blockEntity = ((NeoForgeWorldGenRegion) world).getBlockEntity(new BlockPos(x, y, z));

        if (blockEntity == null) {
            return null;
        }

        CompoundTag nbt = blockEntity.saveCustomOnly(blockEntity.getLevel().registryAccess());
        nbt.remove("x");
        nbt.remove("y");
        nbt.remove("z");

        return getNBTFromNMSTagCompound(null, nbt);
    }

    private static Object getValueFromNms(Tag inbt)
    {
        NamedBinaryTag.Type type = NamedBinaryTag.Type.values()[inbt.getId()];
        return switch (type) {
            case TAG_Byte -> ((ByteTag) inbt).getAsByte();
            case TAG_Short -> ((ShortTag) inbt).getAsShort();
            case TAG_Int -> ((IntTag) inbt).getAsInt();
            case TAG_Long -> ((LongTag) inbt).getAsLong();
            case TAG_Float -> ((FloatTag) inbt).getAsFloat();
            case TAG_Double -> ((DoubleTag) inbt).getAsDouble();
            case TAG_Byte_Array -> ((ByteArrayTag) inbt).getAsByteArray();
            case TAG_String -> inbt.getAsString();
            case TAG_Int_Array -> ((IntArrayTag) inbt).getAsIntArray();
            default -> throw new IllegalArgumentException(type + "doesn't have a simple value!");
        };
    }

    public static CompoundTag getNMSFromNBTTagCompound(NamedBinaryTag compoundTag)
    {
        CompoundTag nmsTag = new CompoundTag();
        NamedBinaryTag[] childTags = (NamedBinaryTag[]) compoundTag.getValue();
        if (childTags == null)
        {
            return nmsTag;
        }
        for (NamedBinaryTag tag : childTags)
        {
            switch (tag.getType())
            {
                case TAG_End:
                    break;
                case TAG_Byte:
                case TAG_Short:
                case TAG_Int:
                case TAG_Long:
                case TAG_Float:
                case TAG_Double:
                case TAG_Byte_Array:
                case TAG_String:
                case TAG_Int_Array:
                    nmsTag.put(tag.getName(), createTagNms(tag.getType(), tag.getValue()));
                    break;
                case TAG_List:
                    nmsTag.put(tag.getName(), getNMSFromNBTTagList(tag));
                    break;
                case TAG_Compound:
                    nmsTag.put(tag.getName(), getNMSFromNBTTagCompound(tag));
                    break;
                default:
                    break;
            }
        }
        return nmsTag;
    }

    private static ListTag getNMSFromNBTTagList(NamedBinaryTag listTag)
    {
        ListTag nmsTag = new ListTag();
        NamedBinaryTag[] childTags = (NamedBinaryTag[]) listTag.getValue();
        for (NamedBinaryTag tag : childTags)
        {
            switch (tag.getType())
            {
                case TAG_Byte:
                case TAG_Short:
                case TAG_Int:
                case TAG_Long:
                case TAG_Float:
                case TAG_Double:
                case TAG_Byte_Array:
                case TAG_String:
                case TAG_Int_Array:
                    nmsTag.add(createTagNms(tag.getType(), tag.getValue()));
                    break;
                case TAG_List:
                    nmsTag.add(getNMSFromNBTTagList(tag));
                    break;
                case TAG_Compound:
                    nmsTag.add(getNMSFromNBTTagCompound(tag));
                    break;
                case TAG_End:
                default:
                    break;
            }
        }
        return nmsTag;
    }

    private static Tag createTagNms(NamedBinaryTag.Type type, Object value)
    {
        return switch (type) {
            case TAG_Byte -> ByteTag.valueOf((Byte) value);
            case TAG_Short -> ShortTag.valueOf((Short) value);
            case TAG_Int -> IntTag.valueOf((Integer) value);
            case TAG_Long -> LongTag.valueOf((Long) value);
            case TAG_Float -> FloatTag.valueOf((Float) value);
            case TAG_Double -> DoubleTag.valueOf((Double) value);
            case TAG_Byte_Array -> new ByteArrayTag((byte[]) value);
            case TAG_String -> StringTag.valueOf((String) value);
            case TAG_Int_Array -> new IntArrayTag((int[]) value);
            default -> throw new IllegalArgumentException(type + "doesn't have a simple value!");
        };
    }
}
