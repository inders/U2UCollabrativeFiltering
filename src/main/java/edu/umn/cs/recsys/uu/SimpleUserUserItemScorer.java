package edu.umn.cs.recsys.uu;

import it.unimi.dsi.fastutil.longs.LongSet;
import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.cursors.Cursor;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.History;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;

/**
 * User-user item scorer.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleUserUserItemScorer extends AbstractItemScorer {
    private final UserEventDAO userDao;
    private final ItemEventDAO itemDao;
    private CosineVectorSimilarity cosineVectorSimilarity = new CosineVectorSimilarity();


    @Inject
    public SimpleUserUserItemScorer(UserEventDAO udao, ItemEventDAO idao) {
        userDao = udao;
        itemDao = idao;
    }

    @Override
    public void score(long user, @Nonnull MutableSparseVector scores) {
        SparseVector userVector = getUserRatingVector(user);

        // TODO Score items for this user using user-user collaborative filtering

        //1. TODO : find average rating for all users in the system
        Map<Long, Double> averageRatingMap = getAverageRatingsForAllUsers();

        //2. TODO : Generate user vectors with dimensions being item(movie) centered around their mean rating
        Map<Long, SparseVector> userVectors = getUserVectorsCenteredOnMean();

        /**
         * 3. TODO Find 30 users similar to this user using Cosine Similarlity between the users’ mean-centered rating vectors
         *         who have rated the item
         * 4. TODO For users 'V' in neighbourhood of user "U" = Average Rating of User U + weighted average( similarity(U,V)
         *        (Rating of User V for item i - Average rating of user V))

         */

        //scores is a Vector with dimensions on itemID instead of rating it will contain the final score
        for (VectorEntry vectorEntry : scores.fast(VectorEntry.State.EITHER)) {
            long itemID = vectorEntry.getKey();

            /**
             *  for each Item do following
             *  1. Find users who rated this item
             *  2. Filter 30 similar users based on similarlity
             *  3. Now apply the weigthed average formula to find prediction of user for itemID
             */
            LongSet userSet = getUsersWhoRated(itemID);
            Set<Long> thirtySimilarUsers = get30SimilarUsers(userSet, user, userVectors);

            double avgUserRatingForItem = averageRatingMap.get(user);

            double weightedRating=0;
            double similarityWeight=0;

            for (Long similarUser : thirtySimilarUsers) {
                double similiarity = getCosineSimilarlityBetweenUsers(similarUser, user, userVectors);
                double avgRatingofSimilarUser = averageRatingMap.get(similarUser);
                double ratingForItemByUser = getRatingForItem(itemID, similarUser);

                weightedRating += similiarity * (ratingForItemByUser - avgRatingofSimilarUser);
                similarityWeight += Math.abs(similiarity);
            }

            double scoreForItem =  avgUserRatingForItem + ( weightedRating/ similarityWeight);
            scores.set(itemID, scoreForItem);
        }


    }

    private double getRatingForItem(long itemId, long userId){
        SparseVector ratingVector = getUserRatingVector(userId);
        return ratingVector.get(itemId);
    }

    /**
     * Get userID's who rate itemid
     *
     */
    private LongSet getUsersWhoRated(long itemId) {
        return itemDao.getUsersForItem(itemId);
    }

    /**
     * Given a set of users and userId return 30 users based on their cosine similarlity score sorted in desecding order
     * @param users
     * @param userId
     * @return
     */
    private Set<Long> get30SimilarUsers(LongSet users, long userId,
                                        Map<Long, SparseVector> userVectorCenteredOnMean) {

        class UserSimilarlity {
            private Long user;
            private Double similarity;

            UserSimilarlity(Long user, Double similarity) {
                this.user = user;
                this.similarity = similarity;
            }

            Long getUser() {
                return user;
            }

            Double getSimilarity() {
                return similarity;
            }

        }

        class UserSimilarlityComparator implements Comparator<UserSimilarlity> {

            @Override
            public int compare(UserSimilarlity o1, UserSimilarlity o2) {
                return Double.compare(o1.getSimilarity(), o2.getSimilarity());
            }
        }

        List<UserSimilarlity> userSimilarlityList = new ArrayList<UserSimilarlity>();
        Set<Long> thirtySimilarUsers = new HashSet<Long>();

        //find similarlity of users w.r.t. userId
        Iterator<Long> it = users.iterator();
        while (it.hasNext()) {
            Long user = it.next();
            double similarity = getCosineSimilarlityBetweenUsers(user, userId, userVectorCenteredOnMean);
            userSimilarlityList.add(new UserSimilarlity(user, similarity));
        }
        //Sort the list based on similarlity values in descending order of similarlity
        Comparator<UserSimilarlity> reverseComparator = Collections.reverseOrder(new UserSimilarlityComparator());
        Collections.sort(userSimilarlityList, reverseComparator);

        //return only 30 users
        List<UserSimilarlity> finalThirtyList = new ArrayList<UserSimilarlity>();
        int i=0;
         while (finalThirtyList.size() != 30){
           UserSimilarlity tmpUserSim = userSimilarlityList.get(i++);
           //check if it's the same user as the input userId skip it from top 30
           if (tmpUserSim.getUser() != userId){
               finalThirtyList.add(tmpUserSim);
           }
        }

        for (UserSimilarlity userSimilarlity : finalThirtyList) {
            thirtySimilarUsers.add(userSimilarlity.getUser());
        }
        return thirtySimilarUsers;
    }

    /**
     *  Returns a map of all users and their rating vectors centered around mean rating
     */
    private Map<Long, SparseVector> getUserVectorsCenteredOnMean() {
        Map<Long, SparseVector> userVectors = new HashMap<Long, SparseVector>();
        Cursor<UserHistory<Event>> cursor =  userDao.streamEventsByUser();
        for (UserHistory userHistory : cursor.fast()) {
            long userId = userHistory.getUserId();
            SparseVector userRatingVector = getUserRatingVector(userId);

            MutableSparseVector meanVector = MutableSparseVector.create(userRatingVector.keySet());
            meanVector.fill(userRatingVector.mean());

            MutableSparseVector userVectorAroundMean = userRatingVector.mutableCopy();
            userVectorAroundMean.subtract(meanVector);

            userVectors.put(userId, userVectorAroundMean);
        }
        return userVectors;
    }


    /**
     * Get Average Ratings for all users in the system
     * @return
     */
    private Map<Long, Double> getAverageRatingsForAllUsers() {
        Map<Long, Double> avgRatingUserMap = new HashMap<Long, Double>();
        //Get all users present in the data
        Cursor<UserHistory<Event>> cursor =  userDao.streamEventsByUser();
        for (UserHistory userHistory : cursor.fast()) {
            long userId = userHistory.getUserId();
            //for each userId -> getAverageRating from the Rating Vector of this user
            avgRatingUserMap.put(userId, getAverageRatingOfUser(getUserRatingVector(userId)));
        }
        return avgRatingUserMap;
    }

    /**
     * Given a user vector of ratings for a user returns the average rating on this user
     *
     * @param userRatings
     * @return
     */
    private double getAverageRatingOfUser(SparseVector userRatings) {
        return userRatings.mean();
    }

    /**
     * Get Cosine Similarlity between two user vectors
     *
     * @param user1
     * @param user2
     * @return
     */
    private double getCosineSimilarlityBetweenUsers(long user1, long user2, Map<Long, SparseVector> userVectorCenteredOnMean) {
        SparseVector user1Vector = userVectorCenteredOnMean.get(user1);
        SparseVector user2Vector = userVectorCenteredOnMean.get(user2);

        return cosineVectorSimilarity.similarity(user1Vector, user2Vector);
    }


    /**
     * Get a user's rating vector.
     * @param user The user ID.
     * @return The rating vector.
     */
    private SparseVector getUserRatingVector(long user) {
        UserHistory<Rating> history = userDao.getEventsForUser(user, Rating.class);
        if (history == null) {
            history = History.forUser(user);
        }
        return RatingVectorUserHistorySummarizer.makeRatingVector(history);
    }
}
