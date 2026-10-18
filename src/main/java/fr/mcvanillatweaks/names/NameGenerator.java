package fr.mcvanillatweaks.names;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Draws "First Surname" names from the enabled lists.
 * <ul>
 * <li>Every first name of the right gender has the same chance, whatever list it is in.</li>
 * <li>The surname comes from the same list when it has surnames (names stay coherent), else from any enabled list.</li>
 * <li>A missing part (no female first names, no surnames) comes from MineColonies' own name file.</li>
 * <li>Names already carried in the colony are avoided, then first names already carried, as far as possible.</li>
 * </ul>
 */
public final class NameGenerator
{
    private static final int TRIES = 40;

    private NameGenerator()
    {
    }

    /**
     * @param fallbackFirst    first names of this gender to use when no list has any (MineColonies' name file).
     * @param fallbackSurnames surnames to use when no list has any.
     * @param taken            full names already carried in the colony.
     * @return null when the lists have neither first names of this gender nor surnames (MineColonies names it).
     */
    @Nullable
    public static String newName(final Random random, final boolean female, final List<NameList> lists, final List<String> fallbackFirst,
      final List<String> fallbackSurnames, final Set<String> taken)
    {
        final boolean ownFirst = lists.stream().anyMatch(list -> !list.firstNames(female).isEmpty());
        final boolean ownSurnames = lists.stream().anyMatch(list -> !list.surnames().isEmpty());
        if (!ownFirst && !ownSurnames)
        {
            return null;
        }
        final List<String> allSurnames = new ArrayList<>();
        lists.forEach(list -> allSurnames.addAll(list.surnames()));
        final Set<String> takenFirst = firstNames(taken);

        String backup = null;
        String last = null;
        for (int i = 0; i < TRIES; i++)
        {
            final NameList source = ownFirst ? pickList(random, lists, female) : null;
            final String first = pick(random, source == null ? fallbackFirst : source.firstNames(female));
            final String surname = source != null && !source.surnames().isEmpty() ? pick(random, source.surnames())
              : !allSurnames.isEmpty() ? pick(random, allSurnames) : pick(random, fallbackSurnames);
            last = join(first, surname);
            if (last.isEmpty())
            {
                return null;
            }
            if (taken.contains(last))
            {
                continue;
            }
            if (!takenFirst.contains(first))
            {
                return last;
            }
            if (backup == null)
            {
                backup = last;
            }
        }
        return backup != null ? backup : last;
    }

    /**
     * A child keeps the surname of one of its parents (as MineColonies does); only the first name is drawn.
     *
     * @return null when there is no first name of this gender in the lists, or no parent surname to keep.
     */
    @Nullable
    public static String childName(final Random random, final boolean female, final List<NameList> lists, final String parentA,
      final String parentB, final Set<String> taken)
    {
        final List<String> surnames = new ArrayList<>(2);
        for (final String parent : new String[] {parentA, parentB})
        {
            final String surname = surnameOf(parent);
            if (surname != null)
            {
                surnames.add(surname);
            }
        }
        if (surnames.isEmpty() || lists.stream().allMatch(list -> list.firstNames(female).isEmpty()))
        {
            return null;
        }
        final String surname = surnames.get(random.nextInt(surnames.size()));
        final Set<String> takenFirst = firstNames(taken);
        String backup = null;
        String last = null;
        for (int i = 0; i < TRIES; i++)
        {
            final String first = pick(random, pickList(random, lists, female).firstNames(female));
            last = join(first, surname);
            if (taken.contains(last))
            {
                continue;
            }
            if (!takenFirst.contains(first))
            {
                return last;
            }
            if (backup == null)
            {
                backup = last;
            }
        }
        return backup != null ? backup : last;
    }

    /**
     * What follows the first name: "Jean de la Fontaine" gives "de la Fontaine". The middle initial of MineColonies'
     * three-part names is dropped ("Jean M. Dupont" gives "Dupont").
     */
    @Nullable
    public static String surnameOf(@Nullable final String name)
    {
        if (name == null)
        {
            return null;
        }
        final String[] words = name.strip().split("\\s+");
        int from = 1;
        if (words.length > 2 && words[1].length() == 2 && words[1].endsWith("."))
        {
            from = 2;
        }
        return words.length > from ? String.join(" ", List.of(words).subList(from, words.length)) : null;
    }

    private static Set<String> firstNames(final Set<String> names)
    {
        final Set<String> first = new HashSet<>();
        for (final String name : names)
        {
            final int space = name.indexOf(' ');
            first.add(space < 0 ? name : name.substring(0, space));
        }
        return first;
    }

    /** A list, weighted by its number of first names of this gender. */
    private static NameList pickList(final Random random, final List<NameList> lists, final boolean female)
    {
        int total = 0;
        for (final NameList list : lists)
        {
            total += list.firstNames(female).size();
        }
        int index = random.nextInt(total);
        for (final NameList list : lists)
        {
            index -= list.firstNames(female).size();
            if (index < 0)
            {
                return list;
            }
        }
        return lists.getLast();
    }

    @Nullable
    private static String pick(final Random random, final List<String> names)
    {
        return names.isEmpty() ? null : names.get(random.nextInt(names.size()));
    }

    private static String join(@Nullable final String first, @Nullable final String surname)
    {
        if (first == null)
        {
            return surname == null ? "" : surname;
        }
        return surname == null ? first : first + " " + surname;
    }
}
