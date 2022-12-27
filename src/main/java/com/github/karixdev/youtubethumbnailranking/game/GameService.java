package com.github.karixdev.youtubethumbnailranking.game;

import com.github.karixdev.youtubethumbnailranking.game.exception.GameHasAlreadyEndedException;
import com.github.karixdev.youtubethumbnailranking.game.exception.GameHasEndedException;
import com.github.karixdev.youtubethumbnailranking.game.exception.GameHasNotEndedException;
import com.github.karixdev.youtubethumbnailranking.game.exception.InvalidWinnerIdException;
import com.github.karixdev.youtubethumbnailranking.game.payload.request.GameResultRequest;
import com.github.karixdev.youtubethumbnailranking.game.payload.response.GameResponse;
import com.github.karixdev.youtubethumbnailranking.rating.RatingService;
import com.github.karixdev.youtubethumbnailranking.security.UserPrincipal;
import com.github.karixdev.youtubethumbnailranking.shared.exception.PermissionDeniedException;
import com.github.karixdev.youtubethumbnailranking.shared.exception.ResourceNotFoundException;
import com.github.karixdev.youtubethumbnailranking.shared.payload.response.SuccessResponse;
import com.github.karixdev.youtubethumbnailranking.thumnail.Thumbnail;
import com.github.karixdev.youtubethumbnailranking.thumnail.ThumbnailService;
import com.github.karixdev.youtubethumbnailranking.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GameService {
    private final ThumbnailService thumbnailService;
    private final RatingService ratingService;
    private final GameRepository repository;
    private final Clock clock;
    private final GameProperties properties;

    public GameResponse start(UserPrincipal userPrincipal) {
        User user = userPrincipal.getUser();

        List<Game> userGames =
                repository.findByUserOrderByLastActivityDesc(user);

        LocalDateTime now = LocalDateTime.now(clock);

        if (!userGames.isEmpty()) {
            LocalDateTime expectedEnd = userGames.get(0)
                    .getLastActivity()
                    .plusMinutes(properties.getDuration());

            if (now.isBefore(expectedEnd) && !userGames.get(0).getHasEnded()) {
                throw new GameHasNotEndedException();
            }
        }

        Thumbnail thumbnail1 = thumbnailService.getRandomThumbnail();
        Thumbnail thumbnail2 = ratingService.pickOpponent(thumbnail1, user, null);

        Game game = repository.save(Game.builder()
                .thumbnail1(thumbnail1)
                .thumbnail2(thumbnail2)
                .user(user)
                .lastActivity(now)
                .build());

        return new GameResponse(game);
    }

    @Transactional
    public GameResponse result(Long gameId, GameResultRequest payload, UserPrincipal userPrincipal) {
        Game game = repository.findById(gameId)
                .orElseThrow(() -> {
                    throw new ResourceNotFoundException(
                            "Game with provided id was not found");
                });

        User user = userPrincipal.getUser();

        if (!game.getUser().equals(user)) {
            throw new PermissionDeniedException("You are not the owner of the game");
        }

        LocalDateTime now = LocalDateTime.now(clock);

        if (now.isAfter(game.getLastActivity().plusMinutes(properties.getDuration())) || game.getHasEnded()) {
            throw new GameHasEndedException();
        }

        Long winnerId = payload.getWinnerId();

        if (!winnerId.equals(game.getThumbnail1().getId()) &&
                !winnerId.equals(game.getThumbnail2().getId())) {
            throw new InvalidWinnerIdException();
        }

        Thumbnail winner = game.getThumbnail1().getId().equals(winnerId)
                ? game.getThumbnail1() : game.getThumbnail2();

        Thumbnail loser = game.getThumbnail1().getId().equals(winnerId)
                ? game.getThumbnail2() : game.getThumbnail1();

        // update ratings
        ratingService.updateRatings(winner, loser, user);

        // pick new opponent
        Thumbnail newOpponent = ratingService.pickOpponent(winner, user, loser);

        if (game.getThumbnail2().getId().equals(loser.getId())) {
            game.setThumbnail2(newOpponent);
        } else {
            game.setThumbnail1(newOpponent);
        }

        // update last activity
        game.setLastActivity(now);

        game = repository.save(game);

        return new GameResponse(game);
    }

    @Transactional
    public SuccessResponse end(Long gameId, UserPrincipal userPrincipal) {
        Game game = repository.findById(gameId)
                .orElseThrow(() -> {
                    throw new ResourceNotFoundException(
                            "Game with provided id was not found");
                });

        User user = userPrincipal.getUser();

        if (!game.getUser().equals(user)) {
            throw new PermissionDeniedException("You are not the owner of the game");
        }

        if (game.getHasEnded()) {
            throw new GameHasAlreadyEndedException();
        }

        game.setHasEnded(Boolean.TRUE);
        repository.save(game);

        return new SuccessResponse();
    }
}
