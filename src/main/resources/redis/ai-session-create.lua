if redis.call('exists', KEYS[1]) == 1 then return 0 end
redis.call('hset', KEYS[1], 'owner', ARGV[1], 'version', '0', 'data', ARGV[2])
redis.call('pexpire', KEYS[1], ARGV[3])
return 1
