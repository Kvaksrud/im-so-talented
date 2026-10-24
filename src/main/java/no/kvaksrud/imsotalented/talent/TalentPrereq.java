package no.kvaksrud.imsotalented.talent;

/**
 * One prerequisite on a talent: the talent that must be bought and the level it must have reached. A talent is
 * unlocked once every one of its prereqs is met; the empty list means unlocked from the start.
 */
public record TalentPrereq(Talent talent, int minLevel) {
}
