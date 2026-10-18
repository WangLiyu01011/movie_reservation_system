if redis.call('hget', KEYS[1], 'owner') ~= ARGV[1] then return 0 end
redis.call('del', KEYS[1], KEYS[2])
return 1
