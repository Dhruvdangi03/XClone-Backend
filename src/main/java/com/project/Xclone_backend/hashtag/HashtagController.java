package com.project.Xclone_backend.hashtag;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.Xclone_backend.hashtag.HashtagDtos.TrendingHashtag;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/trending")
@RequiredArgsConstructor
public class HashtagController {

    private final HashtagService hashtagService;

    @GetMapping("/hashtags")
    public List<TrendingHashtag> trending(@RequestParam(required = false) Integer hours,
            @RequestParam(required = false) Integer limit) {
        return hashtagService.trending(hours, limit);
    }
}
