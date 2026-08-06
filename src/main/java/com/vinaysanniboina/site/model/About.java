package com.vinaysanniboina.site.model;

import java.util.List;

/**
 * Everything in {@code content/about.yaml}. This is the single source of truth for the About page
 * <em>and</em> the HTML CV page — edit once, both update.
 *
 * <p>There is deliberately no "experience" type here. Add one only when there is real employment
 * to put in it.
 */
public record About(
        String headline,
        /** markdown, several paragraphs */
        String profile,
        List<Education> education,
        List<SkillGroup> skills,
        List<Membership> memberships,
        List<Language> languages,
        List<String> certifications,
        List<String> interests,
        /** slugs of projects to list on the CV page, in order */
        List<String> cvProjects
) {
    public List<Education> educationList() { return education == null ? List.of() : education; }
    public List<SkillGroup> skillList() { return skills == null ? List.of() : skills; }
    public List<Membership> membershipList() { return memberships == null ? List.of() : memberships; }
    public List<Language> languageList() { return languages == null ? List.of() : languages; }
    public List<String> certificationList() { return certifications == null ? List.of() : certifications; }
    public List<String> interestList() { return interests == null ? List.of() : interests; }
    public List<String> cvProjectList() { return cvProjects == null ? List.of() : cvProjects; }

    public record Education(
            String qualification,
            String field,
            String grade,
            String institution,
            String location,
            String dates,
            List<String> modules,
            List<String> highlights,
            String note
    ) {
        public List<String> moduleList() { return modules == null ? List.of() : modules; }
        public List<String> highlightList() { return highlights == null ? List.of() : highlights; }
    }

    /**
     * A row in the skills matrix. {@code level} is a 1-5 integer used for the bar; leave it out and
     * the skill renders as a plain tag. Only claim a level you would defend in an interview.
     */
    public record SkillGroup(String group, List<Skill> items) {
        public List<Skill> itemList() { return items == null ? List.of() : items; }

        /** Just the names, for the CV page. Templates cannot map over a list themselves. */
        public List<String> names() {
            return itemList().stream().map(Skill::name).toList();
        }

        public record Skill(String name, Integer level, String context) {
            public int levelOrZero() { return level == null ? 0 : level; }
        }
    }

    public record Membership(String body, String grade, String since, String url) {}

    public record Language(String name, String level) {}
}
